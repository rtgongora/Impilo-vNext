#!/usr/bin/env python3
"""Generate a minimal valid DICOM Part-10 file (Secondary Capture, 2x2, 8-bit).

No third-party deps (pydicom unavailable on the preview VM). Explicit VR
Little Endian throughout. Enough for Orthanc to ingest a real study so the
imaging pipeline (Orthanc -> pacs-adapter -> OROS) can be proven end-to-end.

Usage:
  python3 minimal-dicom.py OUT.dcm STUDY_UID SERIES_UID SOP_UID PATIENT_ID PATIENT_NAME ACCESSION
"""
import struct
import sys


def _even(value: bytes, pad: bytes = b"\x00") -> bytes:
    return value if len(value) % 2 == 0 else value + pad


def element(group: int, elem: int, vr: str, value: bytes) -> bytes:
    value = _even(value, b" " if vr in ("UI", "SH", "CS", "DA", "PN", "LO") else b"\x00")
    if vr in ("OB", "OW", "SQ", "UN", "UT"):
        return struct.pack("<HH2sHI", group, elem, vr.encode(), 0, len(value)) + value
    return struct.pack("<HH2sH", group, elem, vr.encode(), len(value)) + value


def ui(v: str) -> bytes:
    return _even(v.encode(), b"\x00")


def main() -> None:
    out, study_uid, series_uid, sop_uid, patient_id, patient_name, accession = sys.argv[1:8]
    sop_class = "1.2.840.10008.5.1.4.1.1.7"  # Secondary Capture
    transfer_syntax = "1.2.840.10008.1.2.1"  # Explicit VR Little Endian
    impl_uid = "1.2.826.0.1.3680043.10.9999.1"

    meta_body = b"".join([
        element(0x0002, 0x0001, "OB", b"\x00\x01"),
        element(0x0002, 0x0002, "UI", ui(sop_class)),
        element(0x0002, 0x0003, "UI", ui(sop_uid)),
        element(0x0002, 0x0010, "UI", ui(transfer_syntax)),
        element(0x0002, 0x0012, "UI", ui(impl_uid)),
    ])
    meta = element(0x0002, 0x0000, "UL", struct.pack("<I", len(meta_body))) + meta_body

    dataset = b"".join([
        element(0x0008, 0x0016, "UI", ui(sop_class)),
        element(0x0008, 0x0018, "UI", ui(sop_uid)),
        element(0x0008, 0x0020, "DA", b"20260703"),
        element(0x0008, 0x0050, "SH", accession.encode()),
        element(0x0008, 0x0060, "CS", b"OT"),
        element(0x0010, 0x0010, "PN", patient_name.encode()),
        element(0x0010, 0x0020, "LO", patient_id.encode()),
        element(0x0020, 0x000D, "UI", ui(study_uid)),
        element(0x0020, 0x000E, "UI", ui(series_uid)),
        element(0x0020, 0x0011, "IS", b"1"),
        element(0x0020, 0x0013, "IS", b"1"),
        element(0x0028, 0x0002, "US", struct.pack("<H", 1)),
        element(0x0028, 0x0004, "CS", b"MONOCHROME2"),
        element(0x0028, 0x0010, "US", struct.pack("<H", 2)),
        element(0x0028, 0x0011, "US", struct.pack("<H", 2)),
        element(0x0028, 0x0100, "US", struct.pack("<H", 8)),
        element(0x0028, 0x0101, "US", struct.pack("<H", 8)),
        element(0x0028, 0x0102, "US", struct.pack("<H", 7)),
        element(0x0028, 0x0103, "US", struct.pack("<H", 0)),
        element(0x7FE0, 0x0010, "OB", b"\x00\x40\x80\xff"),
    ])

    with open(out, "wb") as f:
        f.write(b"\x00" * 128)
        f.write(b"DICM")
        f.write(meta)
        f.write(dataset)
    print(out)


if __name__ == "__main__":
    main()
