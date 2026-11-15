import {scorePersona,cruiseActive,TRAIT_NAMES} from './scoring.mjs';
export {cruiseActive};
const finite=x=>typeof x==='number'&&Number.isFinite(x);
const clamp=x=>Math.max(0,Math.min(1,x));
export const INPUT_ROLES={sdkDiagnostic:'sdk_status_only',
 averageSpeedKmh:'derived_or_external_context',harshAccelPer100Km:'optional_validated_kinematics',harshBrakePer100Km:'optional_validated_kinematics',mediaVolumePercent:'optional_media_context',passengerCount:'optional_seat_count',
 odometerKm:'initial_character_and_trip_history',modelYear:'initial_character',
 drivingMode:'current_driving_context_and_usage_history',cruiseControlState:'current_driving_context_and_usage_history',
 passenger:'current_company_and_aggregate_history',hvacAuto:'current_climate_and_setting_history',targetCabinC:'current_comfort_and_setting_history',
 energyPercent:'current_energy_and_refill_history',actualCabinC:'current_comfort',hvacOn:'current_comfort',charging:'current_charging_switch',
 parked:'current_context_and_trip_boundary',safetyWarning:'current_warning',washerFluidWarning:'current_warning',
 engineOilState:'current_maintenance_context',brakeFluidState:'current_maintenance_context',
 latitude:'place_and_route_history',longitude:'place_and_route_history',
 fanSpeedLevel:'current_climate_setting',batteryCapacityRaw:'body_context_raw_unit',rangeRemainingRaw:'range_context_raw_unit',
 cruiseTargetSpeedRaw:'cruise_target_context_raw_unit',tripDistanceRaw:'trip_context_raw_unit',tripTimeRaw:'trip_context_raw_unit',
 ambientBrightness:'current_ambience',ambientColor:'current_ambience',tripMinutes:'current_fatigue'
};

export function bootstrapFromObserved(observed={},now=Date.now()) {
 const get=k=>observed[k]?.value;
 const current=k=>observed[k]&&now-observed[k].at<=30000?get(k):null;
 const local=new Date(now+9*3600000);
 const input={source:'sdk_observations',passenger:current('passenger'),passengerCount:current('passengerCount'),
  averageSpeedKmh:current('averageSpeedKmh'),harshAccelPer100Km:current('harshAccelPer100Km'),
  harshBrakePer100Km:current('harshBrakePer100Km'),mediaVolumePercent:current('mediaVolumePercent'),
  hourLocal:Math.floor((local.getUTCHours()+local.getUTCMinutes()/60)*4)/4,
  weekend:[0,6].includes(local.getUTCDay()),cabinTemperatureC:current('actualCabinC'),
  targetTemperatureC:current('targetCabinC'),energyPercent:current('energyPercent'),
  cruiseControlState:current('cruiseControlState'),drivingMode:current('drivingMode')};
 const contextScore=scorePersona(input);
 const km=get('odometerKm'),year=get('modelYear'),years=local.getUTCFullYear();
 const experience=[];
 if(finite(km)&&km>=0)experience.push(clamp(Math.log1p(km/50000)/Math.log(21)));
 if(finite(year)&&Number.isInteger(year)&&year>=1950&&year<=years)experience.push(clamp((years-year)/20));
 const seasoning=experience.length?experience.reduce((a,b)=>a+b)/experience.length:.5;
 // A body characteristic can shade the core; occupancy/current settings cannot
 // overwrite it. Learned long-term patterns continue through experience.mjs.
 const traits=Object.fromEntries(TRAIT_NAMES.map(k=>[k,.5]));
 traits.autonomy+=.08*(seasoning-.5);traits.recovery+=.04*(seasoning-.5);
 const confidence=Object.fromEntries(TRAIT_NAMES.map(k=>[k,0]));
 confidence.autonomy=confidence.recovery=experience.length/2;
 const evidence=Object.entries(observed).filter(([k,e])=>e?.value!==null&&e?.value!==undefined&&
  (['odometerKm','modelYear'].includes(k)||now-e.at<=30000))
  .map(([signal,e])=>({signal,value:e.value,at:e.at,source:e.source,role:INPUT_ROLES[signal]||'context'}));
 return {traits,confidence,factors:{vehicleExperience:seasoning,normalized:contextScore.normalized},evidence,
  available:contextScore.ready,contextScore,contextTraits:contextScore.traits,contextConfidence:contextScore.confidence};
}
