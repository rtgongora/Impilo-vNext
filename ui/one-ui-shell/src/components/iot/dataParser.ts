/**
 * dataParser.ts — faithful TypeScript port of the Impilo Practitioner desktop
 * app's `helper/DataParser.js`.
 *
 * Real medical BLE devices (oximeter, glucometer, thermometer, blood-pressure
 * monitor) do NOT speak the standard GATT measurement profiles. They expose
 * vendor-specific services/characteristics and stream raw byte packets. This
 * parser decodes those packets exactly the way the proven desktop app does.
 *
 * Source device byte layouts (verified against real hardware):
 *   Oximeter      : 10-byte packet  →  spo2 = dat[7], pulseRate = dat[6], pi = dat[8]/10
 *   Glucometer    : glucose = hi(dat[4]) + low(dat[5])  (then /18 → mmol/L by caller)
 *   Thermometer   : 8-byte 0xAA header  →  temp = ((dat[4]<<8)|dat[5]) / 100
 *   Bluetooth-Th. : structured multi-candidate temperature decode
 *   BloodPressure : 8-byte packet  →  systolic = dat[3], diastolic = dat[4]
 */

export type DeviceKind =
  | "Oximeter"
  | "BloodOxygen"
  | "Glucometer"
  | "Thermometer"
  | "BluetoothThermometer"
  | "BloodPressure";

export interface DeviceReading {
  spo2: number;
  pulseRate: number;
  pi: number;
  /** raw glucose (mg/dL-ish before /18 conversion) */
  glucose: number;
  celcius: number;
  fahrenheit: number;
  systolic: number;
  diastolic: number;
  feedback: string;
  /** normalised device label emitted to the listener */
  device: string;
}

export interface PackageReceivedListener {
  onReadingChanged: (reading: DeviceReading) => void;
}

export class DataParser {
  private listener: PackageReceivedListener;
  private buffer: Uint8Array = new Uint8Array(10);
  private type: DeviceKind | "" = "";
  private dataUpdated = true;
  private feedback = "";

  constructor(listener: PackageReceivedListener) {
    this.listener = listener;
  }

  private celsiusToFahrenheit(celsius: number): number {
    return (celsius * 9) / 5 + 32;
  }

  add(dat: Uint8Array, type: DeviceKind): void {
    this.buffer = dat;
    this.type = type;
    this.dataUpdated = true;
    this.parseData();
  }

  private emit(partial: Partial<DeviceReading>, device: string): void {
    const reading: DeviceReading = {
      spo2: 0,
      pulseRate: 0,
      pi: 0,
      glucose: 0,
      celcius: 0,
      fahrenheit: 0,
      systolic: 0,
      diastolic: 0,
      feedback: this.feedback,
      device,
      ...partial,
    };
    this.listener.onReadingChanged(reading);
  }

  private parseData(): void {
    if (!this.dataUpdated) return;
    const dat = this.buffer;

    if (this.type === "Oximeter" || this.type === "BloodOxygen") {
      const spo2 = dat[7];
      const pulseRate = dat[6];
      const pi = dat[8] / 10;
      if (spo2 >= 35 && spo2 <= 100 && pulseRate <= 220) {
        this.emit({ spo2, pulseRate, pi }, this.type);
      }
    } else if (this.type === "Glucometer") {
      const glucose = this.parseGlucoseData(dat);
      if (glucose > 0) {
        this.emit({ glucose }, "Glucometer");
      }
    } else if (this.type === "Thermometer") {
      const parsed = this.parseTemperatureData(dat);
      if (parsed) {
        this.emit(
          {
            celcius: parsed.temperature,
            fahrenheit: this.celsiusToFahrenheit(parsed.temperature),
          },
          "Thermometer",
        );
      }
    } else if (this.type === "BluetoothThermometer") {
      const parsed = this.parseBluetoothTemperatureData(dat);
      if (parsed) {
        this.emit(
          {
            celcius: parsed.temperature,
            fahrenheit: this.celsiusToFahrenheit(parsed.temperature),
          },
          "Thermometer",
        );
      }
    } else if (this.type === "BloodPressure") {
      const systolic = dat[3];
      const diastolic = dat[4];
      this.emit({ systolic, diastolic }, "BloodPressure");
    }

    this.dataUpdated = false;
  }

  private parseGlucoseData(dat: Uint8Array): number {
    const hi = dat[4];
    const low = dat[5];
    const hiNumber = parseInt(hi.toString(16), 16);
    const lowNumber = parseInt(low.toString(16), 16);
    return hiNumber + lowNumber;
  }

  private isPlausibleBodyTempCelsius(temp: number | null): temp is number {
    return temp !== null && Number.isFinite(temp) && temp >= 32 && temp <= 43;
  }

  private normalizeToCelsius(value: number | null): number | null {
    if (value === null || !Number.isFinite(value)) return null;
    // Fahrenheit body-temp range only — do not divide 320–430 raw uint16 values.
    if (value >= 89 && value <= 110) {
      return ((value - 32) * 5) / 9;
    }
    return value;
  }

  private parseTemperatureFromText(dat: Uint8Array): number | null {
    try {
      const text = new TextDecoder("utf-8", { fatal: false })
        .decode(dat)
        .replace(/\0/g, "")
        .trim();
      const match = text.match(/(\d{2,3}(?:\.\d{1,2})?)/);
      if (!match) return null;
      return this.normalizeToCelsius(Number.parseFloat(match[1]));
    } catch {
      return null;
    }
  }

  /** Legacy thermometer: 0xAA header, 8 bytes. */
  private parseTemperatureData(dat: Uint8Array): { temperature: number } | null {
    if (dat.length !== 8 || dat[0] !== 0xaa) return null;
    const temperature = ((dat[4] << 8) | dat[5]) / 100.0;
    if (!this.isPlausibleBodyTempCelsius(temperature)) return null;
    return { temperature };
  }

  private tryCandidateTemperature(value: number | null): { temperature: number } | null {
    const norm = this.normalizeToCelsius(value);
    if (this.isPlausibleBodyTempCelsius(norm)) {
      return { temperature: norm };
    }
    return null;
  }

  private centiCelsiusAt(dat: Uint8Array, offset: number, littleEndian = true): number | null {
    if (offset < 0 || offset + 1 >= dat.length) return null;
    const raw = littleEndian
      ? dat[offset] | (dat[offset + 1] << 8)
      : (dat[offset] << 8) | dat[offset + 1];
    return raw / 100.0;
  }

  private tenthsCelsiusAt(dat: Uint8Array, offset: number, littleEndian = true): number | null {
    if (offset < 0 || offset + 1 >= dat.length) return null;
    const raw = littleEndian
      ? dat[offset] | (dat[offset + 1] << 8)
      : (dat[offset] << 8) | dat[offset + 1];
    return raw / 10.0;
  }

  private isHeaderFalsePositive(dat: Uint8Array, temp: number, source: string): boolean {
    if (
      !dat?.length ||
      !Number.isFinite(temp) ||
      Math.abs(temp - 38.9) > 0.15 ||
      dat[0] !== 0x85 ||
      dat[1] !== 0x01
    ) {
      return false;
    }
    return (
      source.includes("tenths-0") ||
      source.includes("tenths-1") ||
      source === "bytes-1-2-le" ||
      (source === "last-bytes-le" && dat.length <= 4)
    );
  }

  private pickBestTemperatureCandidate(
    candidates: Array<{ temperature: number; priority: number; source: string }>,
    dat: Uint8Array,
  ): { temperature: number } | null {
    if (!candidates.length) return null;
    const filtered = candidates.filter(
      (c) => !this.isHeaderFalsePositive(dat, c.temperature, c.source),
    );
    const pool = filtered.length ? filtered : candidates;
    pool.sort((a, b) => b.priority - a.priority);
    return { temperature: pool[0].temperature };
  }

  /** New Bluetooth thermometer — structured multi-candidate decode. */
  private parseBluetoothTemperatureData(dat: Uint8Array): { temperature: number } | null {
    const legacy = this.parseTemperatureData(dat);
    if (legacy) return legacy;

    const candidates: Array<{ temperature: number; priority: number; source: string }> = [];
    const addCandidate = (temp: number | null, priority: number, source: string) => {
      const parsed = this.tryCandidateTemperature(temp);
      if (parsed) candidates.push({ ...parsed, priority, source });
    };

    const fromText = this.parseTemperatureFromText(dat);
    if (fromText !== null) addCandidate(fromText, 95, "text");

    if (dat.length >= 5 && dat[0] <= 0x07) {
      try {
        const view = new DataView(dat.buffer, dat.byteOffset, dat.byteLength);
        let temp = view.getFloat32(1, true);
        if (dat[0] & 0x01) {
          temp = ((temp - 32) * 5) / 9;
        }
        addCandidate(temp, 90, "gatt-float");
      } catch {
        /* fall through */
      }
    }

    if (dat.length >= 2) {
      const lastPriority = dat.length >= 5 ? 88 : 55;
      addCandidate(this.centiCelsiusAt(dat, dat.length - 2, true), lastPriority, "last-bytes-le");
      addCandidate(
        this.tenthsCelsiusAt(dat, dat.length - 2, true),
        lastPriority - 1,
        "last-bytes-tenths-le",
      );
    }

    if (dat.length >= 6) {
      addCandidate(this.centiCelsiusAt(dat, 4, true), 78, "bytes-4-5-le");
      addCandidate(this.tenthsCelsiusAt(dat, 4, true), 79, "bytes-4-5-tenths-le");
    }

    for (let i = 2; i < dat.length - 1; i++) {
      const offsetPriority = 80 + i;
      addCandidate(this.tenthsCelsiusAt(dat, i, true), offsetPriority, `tenths-${i}-le`);
      addCandidate(this.tenthsCelsiusAt(dat, i, false), offsetPriority - 1, `tenths-${i}-be`);
      addCandidate(this.centiCelsiusAt(dat, i, true), offsetPriority - 4, `centi-${i}-le`);
    }

    return this.pickBestTemperatureCandidate(candidates, dat);
  }
}
