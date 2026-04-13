'use client';

import { useState } from 'react';
import {
  Bed, Users, Activity, AlertTriangle, Building2, ArrowRightLeft,
  User, Clock, RefreshCw, ChevronRight, LogOut,
  Heart, Eye, ArrowLeft, X,
} from 'lucide-react';

// ─── Types ───

interface Patient {
  id: string;
  name: string;
  mrn: string;
  diagnosis: string;
  attendingPhysician: string;
  acuityLevel: 'critical' | 'high' | 'medium' | 'low';
  admissionDate: string;
  age: number;
  gender: string;
  vitals?: { bp: string; hr: number; temp: number; spo2: number };
}

interface BedData {
  id: string;
  bedNumber: string;
  wardId: string;
  wardName: string;
  status: 'available' | 'occupied' | 'maintenance' | 'cleaning';
  patient?: Patient;
}

interface Ward {
  id: string;
  name: string;
}

// ─── Mock Data ───

const WARDS: Ward[] = [
  { id: 'ward-med', name: 'Medical Ward' },
  { id: 'ward-surg', name: 'Surgical Ward' },
  { id: 'ward-mat', name: 'Maternity Ward' },
  { id: 'ward-paed', name: 'Paediatric Ward' },
  { id: 'ward-icu', name: 'ICU' },
];

const MOCK_PATIENTS: Patient[] = [
  { id: 'p1', name: 'Thabo Molefe', mrn: 'MRN-10001', diagnosis: 'Pneumonia', attendingPhysician: 'Dr. Nkomo', acuityLevel: 'high', admissionDate: '2026-04-02T08:00:00Z', age: 54, gender: 'Male', vitals: { bp: '140/90', hr: 88, temp: 38.2, spo2: 93 } },
  { id: 'p2', name: 'Naledi Dube', mrn: 'MRN-10002', diagnosis: 'Diabetic ketoacidosis', attendingPhysician: 'Dr. Sibanda', acuityLevel: 'critical', admissionDate: '2026-04-04T14:00:00Z', age: 38, gender: 'Female', vitals: { bp: '100/60', hr: 112, temp: 37.4, spo2: 96 } },
  { id: 'p3', name: 'Sipho Khumalo', mrn: 'MRN-10003', diagnosis: 'Congestive heart failure', attendingPhysician: 'Dr. Nkomo', acuityLevel: 'high', admissionDate: '2026-04-01T10:00:00Z', age: 67, gender: 'Male', vitals: { bp: '150/95', hr: 92, temp: 36.8, spo2: 91 } },
  { id: 'p4', name: 'Lindiwe Zulu', mrn: 'MRN-10004', diagnosis: 'Post appendectomy', attendingPhysician: 'Dr. Mhlanga', acuityLevel: 'medium', admissionDate: '2026-04-05T06:00:00Z', age: 29, gender: 'Female', vitals: { bp: '120/80', hr: 76, temp: 37.0, spo2: 98 } },
  { id: 'p5', name: 'James Moyo', mrn: 'MRN-10005', diagnosis: 'Fractured femur', attendingPhysician: 'Dr. Mhlanga', acuityLevel: 'medium', admissionDate: '2026-04-03T16:00:00Z', age: 45, gender: 'Male', vitals: { bp: '130/85', hr: 80, temp: 36.9, spo2: 97 } },
  { id: 'p6', name: 'Precious Ndlovu', mrn: 'MRN-10006', diagnosis: 'Pre-eclampsia', attendingPhysician: 'Dr. Banda', acuityLevel: 'high', admissionDate: '2026-04-05T20:00:00Z', age: 26, gender: 'Female', vitals: { bp: '160/100', hr: 96, temp: 37.1, spo2: 97 } },
  { id: 'p7', name: 'Rudo Katsande', mrn: 'MRN-10007', diagnosis: 'Normal delivery (postpartum)', attendingPhysician: 'Dr. Banda', acuityLevel: 'low', admissionDate: '2026-04-06T02:00:00Z', age: 32, gender: 'Female', vitals: { bp: '118/72', hr: 70, temp: 36.6, spo2: 99 } },
  { id: 'p8', name: 'Tafadzwa Chirwa', mrn: 'MRN-10008', diagnosis: 'Bronchiolitis', attendingPhysician: 'Dr. Zulu', acuityLevel: 'medium', admissionDate: '2026-04-04T09:00:00Z', age: 3, gender: 'Male', vitals: { bp: '90/60', hr: 130, temp: 38.0, spo2: 94 } },
  { id: 'p9', name: 'Chipo Maposa', mrn: 'MRN-10009', diagnosis: 'Severe malaria', attendingPhysician: 'Dr. Zulu', acuityLevel: 'critical', admissionDate: '2026-04-05T22:00:00Z', age: 7, gender: 'Female', vitals: { bp: '85/55', hr: 140, temp: 39.5, spo2: 90 } },
  { id: 'p10', name: 'Samuel Phiri', mrn: 'MRN-10010', diagnosis: 'Septic shock', attendingPhysician: 'Dr. Sibanda', acuityLevel: 'critical', admissionDate: '2026-04-06T01:00:00Z', age: 60, gender: 'Male', vitals: { bp: '80/50', hr: 120, temp: 39.2, spo2: 88 } },
  { id: 'p11', name: 'Maria Dlamini', mrn: 'MRN-10011', diagnosis: 'Acute asthma exacerbation', attendingPhysician: 'Dr. Nkomo', acuityLevel: 'high', admissionDate: '2026-04-05T11:00:00Z', age: 42, gender: 'Female', vitals: { bp: '135/88', hr: 100, temp: 37.2, spo2: 92 } },
  { id: 'p12', name: 'Peter Banda', mrn: 'MRN-10012', diagnosis: 'Total hip replacement - post-op', attendingPhysician: 'Dr. Mhlanga', acuityLevel: 'medium', admissionDate: '2026-04-04T07:00:00Z', age: 71, gender: 'Male', vitals: { bp: '128/78', hr: 74, temp: 36.7, spo2: 97 } },
];

function generateBeds(): BedData[] {
  const beds: BedData[] = [];
  const wardConfigs: { wardId: string; wardName: string; count: number; patientIndices: number[] }[] = [
    { wardId: 'ward-med', wardName: 'Medical Ward', count: 20, patientIndices: [0, 2, 10] },
    { wardId: 'ward-surg', wardName: 'Surgical Ward', count: 16, patientIndices: [3, 4, 11] },
    { wardId: 'ward-mat', wardName: 'Maternity Ward', count: 14, patientIndices: [5, 6] },
    { wardId: 'ward-paed', wardName: 'Paediatric Ward', count: 12, patientIndices: [7, 8] },
    { wardId: 'ward-icu', wardName: 'ICU', count: 10, patientIndices: [1, 9] },
  ];

  wardConfigs.forEach(cfg => {
    for (let i = 1; i <= cfg.count; i++) {
      let status: BedData['status'] = 'available';
      let patient: Patient | undefined;

      if (i <= cfg.patientIndices.length) {
        status = 'occupied';
        patient = MOCK_PATIENTS[cfg.patientIndices[i - 1]];
      } else if (i === cfg.count) {
        status = 'maintenance';
      }

      beds.push({
        id: `${cfg.wardId}-bed-${i}`,
        bedNumber: `${i}${String.fromCharCode(64 + ((i - 1) % 3 + 1))}`,
        wardId: cfg.wardId,
        wardName: cfg.wardName,
        status,
        patient,
      });
    }
  });

  return beds;
}

const ALL_BEDS = generateBeds();

const ACTIVITY_LOG = [
  { id: 1, action: 'Patient admitted', detail: 'Bed 1A, Medical Ward - Thabo Molefe', actor: 'Sr. Moyo', time: '08:15' },
  { id: 2, action: 'Vitals recorded', detail: 'Bed 2A, ICU - Naledi Dube, BP 100/60', actor: 'Sr. Mabena', time: '08:30' },
  { id: 3, action: 'Discharge completed', detail: 'Bed 5B, Surgical Ward - P. Sithole', actor: 'Dr. Mhlanga', time: '09:00' },
  { id: 4, action: 'Transfer initiated', detail: 'Bed 3A Medical to Bed 1A ICU', actor: 'Dr. Nkomo', time: '09:45' },
  { id: 5, action: 'Medication administered', detail: 'Bed 1A Maternity - Precious Ndlovu', actor: 'Sr. Banda', time: '10:00' },
  { id: 6, action: 'Critical alert', detail: 'Bed 2A Paediatric - SpO2 dropped to 90%', actor: 'Monitor', time: '10:15' },
  { id: 7, action: 'Bed cleaning complete', detail: 'Bed 8B, Surgical Ward', actor: 'Housekeeping', time: '10:30' },
  { id: 8, action: 'Ward round started', detail: 'Medical Ward - Dr. Nkomo', actor: 'System', time: '11:00' },
];

// ─── Helpers ───

function daysAgo(isoDate: string): number {
  return Math.max(0, Math.floor((Date.now() - new Date(isoDate).getTime()) / 86400000));
}

function getAcuityColor(acuity: string): string {
  const map: Record<string, string> = {
    critical: 'bg-red-100 text-red-800 border border-red-200',
    high: 'bg-orange-100 text-orange-800 border border-orange-200',
    medium: 'bg-yellow-100 text-yellow-800 border border-yellow-200',
    low: 'bg-green-100 text-green-800 border border-green-200',
  };
  return map[acuity] || map.medium;
}

function getBedColor(status: BedData['status'], acuity?: string): string {
  if (status === 'available') return 'bg-green-100 border-green-300 text-green-800';
  if (status === 'maintenance') return 'bg-yellow-100 border-yellow-300 text-yellow-800';
  if (status === 'cleaning') return 'bg-purple-100 border-purple-300 text-purple-800';
  if (status === 'occupied' && acuity === 'critical') return 'bg-red-100 border-red-300 text-red-800';
  return 'bg-impilo-100 border-impilo-200 text-impilo-700';
}

// ─── Component ───

type SubView = 'overview' | 'ward-detail';
type WardTab = 'patients' | 'beds' | 'activity';

export function WardManagementPanel() {
  const [beds] = useState<BedData[]>(ALL_BEDS);
  const [subView, setSubView] = useState<SubView>('overview');
  const [selectedWardId, setSelectedWardId] = useState<string | null>(null);
  const [wardTab, setWardTab] = useState<WardTab>('patients');
  const [patientDetailBed, setPatientDetailBed] = useState<BedData | null>(null);
  const [transferDialogOpen, setTransferDialogOpen] = useState(false);
  const [transferBed, setTransferBed] = useState<BedData | null>(null);
  const [transferTargetWard, setTransferTargetWard] = useState('');
  const [transferTargetBed, setTransferTargetBed] = useState('');
  const [transferReason, setTransferReason] = useState('');
  const [dischargeConfirm, setDischargeConfirm] = useState<BedData | null>(null);

  const totalBeds = beds.length;
  const totalOccupied = beds.filter(b => b.status === 'occupied').length;
  const totalAvailable = beds.filter(b => b.status === 'available').length;
  const totalMaintenance = beds.filter(b => b.status === 'maintenance').length;
  const occupancyRate = totalBeds > 0 ? Math.round((totalOccupied / totalBeds) * 100) : 0;
  const criticalPatients = beds.filter(b => b.patient?.acuityLevel === 'critical').length;

  const handleSelectWard = (wardId: string) => {
    setSelectedWardId(wardId);
    setSubView('ward-detail');
    setWardTab('patients');
  };

  const handleBackToOverview = () => {
    setSubView('overview');
    setSelectedWardId(null);
    setPatientDetailBed(null);
  };

  const handleOpenTransfer = (bed: BedData) => {
    setTransferBed(bed);
    setTransferTargetWard('');
    setTransferTargetBed('');
    setTransferReason('');
    setTransferDialogOpen(true);
    setPatientDetailBed(null);
  };

  const handleTransferConfirm = () => {
    if (!transferBed || !transferTargetBed) return;
    setTransferDialogOpen(false);
    setTransferBed(null);
  };

  const handleDischarge = (bed: BedData) => {
    setDischargeConfirm(bed);
    setPatientDetailBed(null);
  };

  const handleDischargeConfirm = () => {
    setDischargeConfirm(null);
  };

  const availableTargetBeds = beds.filter(
    b => b.status === 'available' && (!transferTargetWard || b.wardId === transferTargetWard)
  );

  // ─── Ward Detail View ───
  if (subView === 'ward-detail' && selectedWardId) {
    const ward = WARDS.find(w => w.id === selectedWardId);
    const wardBeds = beds.filter(b => b.wardId === selectedWardId);
    const wardOccupied = wardBeds.filter(b => b.status === 'occupied');
    const wardAvailable = wardBeds.filter(b => b.status === 'available');
    const wardPct = wardBeds.length > 0 ? Math.round((wardOccupied.length / wardBeds.length) * 100) : 0;
    const wardCritical = wardBeds.filter(b => b.patient?.acuityLevel === 'critical').length;

    return (
      <div className="flex flex-col h-full gap-3">
        {/* Header */}
        <div className="flex items-center justify-between shrink-0">
          <div className="flex items-center gap-3">
            <button
              onClick={handleBackToOverview}
              className="inline-flex items-center gap-1 px-3 py-1.5 text-sm rounded-md hover:bg-gray-100 transition-colors"
            >
              <ArrowLeft className="h-4 w-4" />
              Overview
            </button>
            <div>
              <h3 className="text-base font-semibold">{ward?.name}</h3>
              <p className="text-xs text-gray-500">
                {wardOccupied.length}/{wardBeds.length} beds occupied &middot; {wardAvailable.length} available
              </p>
            </div>
          </div>
          <button className="inline-flex items-center gap-1 px-3 py-1.5 text-sm border border-gray-200 rounded-md hover:bg-gray-50">
            <RefreshCw className="h-3.5 w-3.5" />
            Refresh
          </button>
        </div>

        {/* Ward Stats */}
        <div className="grid grid-cols-5 gap-2 shrink-0">
          <div className="bg-white border border-gray-200 rounded-lg p-2.5">
            <div className="text-lg font-bold">{wardBeds.length}</div>
            <p className="text-[10px] text-gray-500">Total Beds</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-lg p-2.5">
            <div className="text-lg font-bold text-impilo-500">{wardOccupied.length}</div>
            <p className="text-[10px] text-gray-500">Occupied</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-lg p-2.5">
            <div className="text-lg font-bold text-green-600">{wardAvailable.length}</div>
            <p className="text-[10px] text-gray-500">Available</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-lg p-2.5">
            <div className={`text-lg font-bold ${wardPct >= 90 ? 'text-red-600' : ''}`}>{wardPct}%</div>
            <p className="text-[10px] text-gray-500">Occupancy</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-lg p-2.5">
            <div className="text-lg font-bold text-red-600">{wardCritical}</div>
            <p className="text-[10px] text-gray-500">Critical</p>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex gap-1 border-b border-gray-200 shrink-0">
          {([
            { key: 'patients' as WardTab, label: 'Patients', icon: Users, count: wardOccupied.length },
            { key: 'beds' as WardTab, label: 'Bed Map', icon: Bed },
            { key: 'activity' as WardTab, label: 'Activity', icon: Activity },
          ]).map(tab => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.key}
                onClick={() => setWardTab(tab.key)}
                className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                  wardTab === tab.key
                    ? 'border-impilo-500 text-impilo-500'
                    : 'border-transparent text-gray-500 hover:text-gray-700'
                }`}
              >
                <Icon className="h-3.5 w-3.5" />
                {tab.label}
                {tab.count !== undefined && (
                  <span className="ml-1 px-1.5 py-0.5 rounded-full bg-gray-100 text-[10px]">{tab.count}</span>
                )}
              </button>
            );
          })}
        </div>

        {/* Tab Content */}
        <div className="flex-1 overflow-auto">
          {/* Patients Tab */}
          {wardTab === 'patients' && (
            <div className="space-y-2">
              {wardOccupied.length === 0 ? (
                <div className="text-center py-8 text-gray-400 text-sm">No patients in this ward</div>
              ) : (
                wardOccupied.map(bed => (
                  <div
                    key={bed.id}
                    onClick={() => setPatientDetailBed(bed)}
                    className="flex items-center justify-between p-3 bg-white border border-gray-200 rounded-lg cursor-pointer hover:bg-gray-50 transition-colors"
                  >
                    <div className="flex items-center gap-3">
                      <div className="h-9 w-9 rounded-lg bg-impilo-100 flex items-center justify-center text-impilo-600 font-bold text-xs">
                        {bed.bedNumber}
                      </div>
                      <div>
                        <p className="font-medium text-sm">{bed.patient?.name}</p>
                        <p className="text-xs text-gray-500">
                          {bed.patient?.mrn} &middot; {bed.patient?.diagnosis}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${getAcuityColor(bed.patient?.acuityLevel || 'medium')}`}>
                        {bed.patient?.acuityLevel}
                      </span>
                      <span className="flex items-center gap-1 text-xs text-gray-500">
                        <Clock className="h-3 w-3" />
                        {daysAgo(bed.patient?.admissionDate || '')}d
                      </span>
                      <ChevronRight className="h-4 w-4 text-gray-400" />
                    </div>
                  </div>
                ))
              )}
            </div>
          )}

          {/* Bed Map Tab */}
          {wardTab === 'beds' && (
            <div>
              <div className="flex gap-4 mb-3 text-xs text-gray-500">
                <span className="flex items-center gap-1"><span className="h-3 w-3 rounded bg-green-400" /> Available</span>
                <span className="flex items-center gap-1"><span className="h-3 w-3 rounded bg-impilo-400" /> Occupied</span>
                <span className="flex items-center gap-1"><span className="h-3 w-3 rounded bg-yellow-400" /> Maintenance</span>
                <span className="flex items-center gap-1"><span className="h-3 w-3 rounded bg-red-400" /> Critical</span>
              </div>
              <div className="grid grid-cols-5 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10 gap-2">
                {wardBeds.map(bed => (
                  <button
                    key={bed.id}
                    onClick={() => bed.status === 'occupied' && setPatientDetailBed(bed)}
                    className={`relative p-2 rounded-lg border-2 text-center transition-all hover:shadow-md ${getBedColor(bed.status, bed.patient?.acuityLevel)} ${bed.status === 'occupied' ? 'cursor-pointer' : 'cursor-default'}`}
                  >
                    <Bed className="h-4 w-4 mx-auto mb-0.5" />
                    <span className="text-xs font-bold">{bed.bedNumber}</span>
                    {bed.patient && (
                      <p className="text-[9px] truncate mt-0.5">{bed.patient.name.split(' ')[1]}</p>
                    )}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Activity Tab */}
          {wardTab === 'activity' && (
            <div className="space-y-2">
              {ACTIVITY_LOG.map(item => (
                <div key={item.id} className="flex items-start gap-3 p-3 rounded-lg bg-gray-50">
                  <div className="h-2 w-2 rounded-full bg-impilo-500 mt-1.5 shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-sm">{item.action}</p>
                    <p className="text-xs text-gray-500">{item.detail} &middot; {item.actor}</p>
                  </div>
                  <span className="text-xs text-gray-400 whitespace-nowrap">{item.time}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Patient Detail Sheet */}
        {patientDetailBed && patientDetailBed.patient && (
          <div className="fixed inset-0 z-50 flex justify-end">
            <div className="absolute inset-0 bg-black/30" onClick={() => setPatientDetailBed(null)} />
            <div className="relative w-full max-w-lg bg-white shadow-xl overflow-auto">
              <div className="sticky top-0 bg-white border-b border-gray-200 px-4 py-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Bed className="h-5 w-5 text-gray-600" />
                  <h3 className="font-semibold">Bed {patientDetailBed.bedNumber}</h3>
                  <span className="px-2 py-0.5 rounded border border-gray-200 text-xs text-gray-600">{patientDetailBed.wardName}</span>
                </div>
                <button onClick={() => setPatientDetailBed(null)} className="p-1 rounded hover:bg-gray-100">
                  <X className="h-5 w-5 text-gray-500" />
                </button>
              </div>

              <div className="p-4 space-y-4">
                {/* Patient Info */}
                <div className="p-4 rounded-lg bg-gray-50 space-y-3">
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-full bg-impilo-100 flex items-center justify-center">
                      <User className="h-5 w-5 text-impilo-500" />
                    </div>
                    <div>
                      <p className="font-semibold">{patientDetailBed.patient.name}</p>
                      <p className="text-xs text-gray-500">{patientDetailBed.patient.mrn} &middot; {patientDetailBed.patient.age}y {patientDetailBed.patient.gender}</p>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3 text-sm">
                    <div>
                      <span className="text-gray-400 text-xs">Diagnosis</span>
                      <p className="font-medium">{patientDetailBed.patient.diagnosis}</p>
                    </div>
                    <div>
                      <span className="text-gray-400 text-xs">Acuity</span>
                      <p>
                        <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${getAcuityColor(patientDetailBed.patient.acuityLevel)}`}>
                          {patientDetailBed.patient.acuityLevel}
                        </span>
                      </p>
                    </div>
                    <div>
                      <span className="text-gray-400 text-xs">Attending Physician</span>
                      <p className="font-medium">{patientDetailBed.patient.attendingPhysician}</p>
                    </div>
                    <div>
                      <span className="text-gray-400 text-xs">Length of Stay</span>
                      <p className="font-medium">{daysAgo(patientDetailBed.patient.admissionDate)} days</p>
                    </div>
                  </div>
                </div>

                {/* Vitals */}
                {patientDetailBed.patient.vitals && (
                  <div>
                    <h4 className="text-sm font-semibold mb-2 flex items-center gap-1.5">
                      <Heart className="h-4 w-4 text-red-500" /> Current Vitals
                    </h4>
                    <div className="grid grid-cols-4 gap-2">
                      {[
                        { label: 'BP', value: patientDetailBed.patient.vitals.bp, unit: 'mmHg' },
                        { label: 'HR', value: patientDetailBed.patient.vitals.hr, unit: 'bpm' },
                        { label: 'Temp', value: patientDetailBed.patient.vitals.temp, unit: '\u00B0C' },
                        { label: 'SpO2', value: patientDetailBed.patient.vitals.spo2, unit: '%' },
                      ].map(v => (
                        <div key={v.label} className="bg-gray-50 rounded-lg p-2 text-center">
                          <p className="text-lg font-bold">{v.value}</p>
                          <p className="text-[10px] text-gray-500">{v.label} ({v.unit})</p>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Actions */}
                <div className="grid grid-cols-3 gap-2">
                  <button className="flex items-center justify-center gap-1.5 px-3 py-2 text-sm border border-gray-200 rounded-lg hover:bg-gray-50">
                    <Eye className="h-3.5 w-3.5" />
                    View Chart
                  </button>
                  <button
                    onClick={() => handleOpenTransfer(patientDetailBed)}
                    className="flex items-center justify-center gap-1.5 px-3 py-2 text-sm border border-gray-200 rounded-lg hover:bg-gray-50"
                  >
                    <ArrowRightLeft className="h-3.5 w-3.5" />
                    Transfer
                  </button>
                  <button
                    onClick={() => handleDischarge(patientDetailBed)}
                    className="flex items-center justify-center gap-1.5 px-3 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700"
                  >
                    <LogOut className="h-3.5 w-3.5" />
                    Discharge
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Transfer Dialog */}
        {transferDialogOpen && transferBed && (
          <div className="fixed inset-0 z-50 flex items-center justify-center">
            <div className="absolute inset-0 bg-black/30" onClick={() => setTransferDialogOpen(false)} />
            <div className="relative bg-white rounded-xl shadow-xl w-full max-w-md p-6 space-y-4">
              <div className="flex items-center gap-2">
                <ArrowRightLeft className="h-5 w-5 text-gray-600" />
                <h3 className="text-lg font-semibold">Transfer Patient</h3>
              </div>

              <div className="p-3 rounded-lg bg-gray-50 text-sm">
                <p className="font-medium">{transferBed.patient?.name}</p>
                <p className="text-xs text-gray-500">
                  Currently in Bed {transferBed.bedNumber} &middot; {transferBed.wardName}
                </p>
              </div>

              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Target Ward</label>
                  <select
                    value={transferTargetWard}
                    onChange={e => { setTransferTargetWard(e.target.value); setTransferTargetBed(''); }}
                    className="w-full h-9 border border-gray-200 rounded-md px-3 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                  >
                    <option value="">Select ward...</option>
                    {WARDS.map(w => (
                      <option key={w.id} value={w.id}>{w.name}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Target Bed</label>
                  <select
                    value={transferTargetBed}
                    onChange={e => setTransferTargetBed(e.target.value)}
                    disabled={!transferTargetWard}
                    className="w-full h-9 border border-gray-200 rounded-md px-3 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 disabled:bg-gray-100 disabled:text-gray-400"
                  >
                    <option value="">{transferTargetWard ? 'Select bed...' : 'Select ward first'}</option>
                    {availableTargetBeds.map(b => (
                      <option key={b.id} value={b.id}>Bed {b.bedNumber} - {b.wardName}</option>
                    ))}
                  </select>
                  {transferTargetWard && availableTargetBeds.length === 0 && (
                    <p className="text-xs text-gray-400 mt-1">No available beds in this ward</p>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Reason for Transfer</label>
                  <textarea
                    value={transferReason}
                    onChange={e => setTransferReason(e.target.value)}
                    placeholder="Clinical reason for bed transfer..."
                    rows={2}
                    className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2">
                <button
                  onClick={() => setTransferDialogOpen(false)}
                  className="px-4 py-2 text-sm border border-gray-200 rounded-lg hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  onClick={handleTransferConfirm}
                  disabled={!transferTargetBed}
                  className="inline-flex items-center gap-1.5 px-4 py-2 text-sm bg-impilo-500 text-white rounded-lg hover:bg-impilo-600 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <ArrowRightLeft className="h-3.5 w-3.5" />
                  Confirm Transfer
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Discharge Confirmation */}
        {dischargeConfirm && (
          <div className="fixed inset-0 z-50 flex items-center justify-center">
            <div className="absolute inset-0 bg-black/30" onClick={() => setDischargeConfirm(null)} />
            <div className="relative bg-white rounded-xl shadow-xl w-full max-w-sm p-6 space-y-4">
              <h3 className="text-lg font-semibold">Confirm Discharge</h3>
              <p className="text-sm text-gray-600">
                Discharge <span className="font-medium">{dischargeConfirm.patient?.name}</span> from Bed {dischargeConfirm.bedNumber}?
              </p>
              <div className="flex justify-end gap-2">
                <button
                  onClick={() => setDischargeConfirm(null)}
                  className="px-4 py-2 text-sm border border-gray-200 rounded-lg hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  onClick={handleDischargeConfirm}
                  className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700"
                >
                  Discharge
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  // ─── Overview Dashboard ───
  return (
    <div className="flex flex-col h-full gap-3">
      {/* Hospital-wide KPI stats */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2 shrink-0">
        {[
          { label: 'Total Beds', value: totalBeds, icon: Bed, iconBg: 'bg-impilo-100', iconColor: 'text-impilo-500' },
          { label: 'Occupied', value: totalOccupied, icon: Users, iconBg: 'bg-orange-100', iconColor: 'text-orange-600' },
          { label: 'Available', value: totalAvailable, icon: Bed, iconBg: 'bg-green-100', iconColor: 'text-green-600' },
          { label: 'Maintenance', value: totalMaintenance, icon: RefreshCw, iconBg: 'bg-yellow-100', iconColor: 'text-yellow-600' },
          { label: 'Occupancy', value: `${occupancyRate}%`, icon: Activity, iconBg: occupancyRate > 90 ? 'bg-red-100' : 'bg-impilo-100', iconColor: occupancyRate > 90 ? 'text-red-600' : 'text-impilo-500', textColor: occupancyRate > 90 ? 'text-red-600' : '' },
          { label: 'Critical', value: criticalPatients, icon: AlertTriangle, iconBg: 'bg-red-100', iconColor: 'text-red-600', textColor: 'text-red-600' },
        ].map(kpi => {
          const Icon = kpi.icon;
          return (
            <div key={kpi.label} className="bg-white border border-gray-200 rounded-lg p-3">
              <div className="flex items-center gap-2">
                <div className={`h-9 w-9 rounded-lg ${kpi.iconBg} flex items-center justify-center`}>
                  <Icon className={`h-4 w-4 ${kpi.iconColor}`} />
                </div>
                <div>
                  <p className={`text-xl font-bold ${kpi.textColor || ''}`}>{kpi.value}</p>
                  <p className="text-[10px] text-gray-500">{kpi.label}</p>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Capacity alert */}
      {beds.some(b => {
        const wb = beds.filter(bb => bb.wardId === b.wardId);
        const occ = wb.filter(bb => bb.status === 'occupied').length;
        return occ >= wb.length && wb.length > 0;
      }) && (
        <div className="flex items-center gap-2 p-3 border border-red-200 bg-red-50 rounded-lg text-red-700 shrink-0">
          <AlertTriangle className="h-4 w-4" />
          <span className="text-sm font-medium">
            {WARDS.filter(w => {
              const wb = beds.filter(b => b.wardId === w.id);
              return wb.length > 0 && wb.filter(b => b.status === 'occupied').length >= wb.length;
            }).map(w => w.name).join(', ')} at full capacity
          </span>
        </div>
      )}

      {/* Ward Cards */}
      <div className="flex-1 overflow-auto">
        <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
          {WARDS.map(ward => {
            const wardBeds = beds.filter(b => b.wardId === ward.id);
            const wardOccupied = wardBeds.filter(b => b.status === 'occupied').length;
            const pct = wardBeds.length > 0 ? Math.round((wardOccupied / wardBeds.length) * 100) : 0;
            const isFull = wardBeds.length > 0 && wardOccupied >= wardBeds.length;
            const wardCritical = wardBeds.filter(b => b.patient?.acuityLevel === 'critical').length;

            return (
              <div
                key={ward.id}
                onClick={() => handleSelectWard(ward.id)}
                className={`bg-white border rounded-lg cursor-pointer hover:shadow-md transition-all ${isFull ? 'border-red-200' : 'border-gray-200 hover:border-impilo-200'}`}
              >
                <div className="px-3 pt-3 pb-2 flex items-center justify-between">
                  <h4 className="text-sm font-semibold flex items-center gap-2">
                    <Building2 className="h-4 w-4 text-gray-400" />
                    {ward.name}
                  </h4>
                  <ChevronRight className="h-4 w-4 text-gray-400" />
                </div>
                <div className="px-3 pb-3">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-2xl font-bold">{wardOccupied}/{wardBeds.length}</span>
                    <span className={`text-sm font-medium ${
                      pct >= 100 ? 'text-red-600' : pct >= 80 ? 'text-yellow-600' : 'text-green-600'
                    }`}>{pct}%</span>
                  </div>
                  {/* Progress bar */}
                  <div className="w-full bg-gray-100 rounded-full h-2 mb-3">
                    <div
                      className={`h-2 rounded-full transition-all ${
                        pct >= 100 ? 'bg-red-500' : pct >= 80 ? 'bg-yellow-500' : 'bg-green-500'
                      }`}
                      style={{ width: `${Math.min(pct, 100)}%` }}
                    />
                  </div>
                  <div className="flex gap-3 text-xs text-gray-500">
                    {wardCritical > 0 && (
                      <span className="flex items-center gap-1">
                        <span className="h-2 w-2 rounded-full bg-red-500" />
                        {wardCritical} critical
                      </span>
                    )}
                    <span className="flex items-center gap-1">
                      <span className="h-2 w-2 rounded-full bg-green-500" />
                      {wardBeds.filter(b => b.status === 'available').length} available
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
