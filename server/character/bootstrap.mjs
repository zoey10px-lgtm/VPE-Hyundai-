// Fictional vehicle-character mapping. These coefficients describe the car's
// character, never a psychological assessment of its driver. No ID/random seed.
const finite=x=>typeof x==='number'&&Number.isFinite(x);
const clamp=x=>Math.max(0,Math.min(1,x));
export function cruiseActive(raw){
 if(typeof raw!=='string')return null;
 const s=raw.trim().toUpperCase();
 if(['INACTIVE','DISABLED','OFF','STANDBY','CANCELLED','CANCELED'].includes(s))return false;
 if(['ACTIVE','ENABLED','ENGAGED','ON','RUNNING'].includes(s))return true;
 return null;
}
export const INPUT_ROLES={
 odometerKm:'initial_character_and_trip_history',modelYear:'initial_character',
 drivingMode:'initial_character_and_usage_history',cruiseControlState:'initial_character_and_usage_history',
 passenger:'initial_character_and_social_history',hvacAuto:'initial_character_and_setting_history',targetCabinC:'initial_character_and_comfort',
 energyPercent:'current_energy_and_refill_history',actualCabinC:'current_comfort',hvacOn:'current_comfort',charging:'current_charging_switch',
 parked:'current_context_and_trip_boundary',safetyWarning:'current_warning',washerFluidWarning:'current_warning',
 engineOilState:'current_maintenance_context',brakeFluidState:'current_maintenance_context',
 latitude:'place_and_route_history',longitude:'place_and_route_history',
 fanSpeedLevel:'current_climate_setting',batteryCapacityRaw:'body_context_raw_unit',rangeRemainingRaw:'range_context_raw_unit',
 cruiseTargetSpeedRaw:'cruise_target_context_raw_unit',tripDistanceRaw:'trip_context_raw_unit',tripTimeRaw:'trip_context_raw_unit',
 ambientBrightness:'current_ambience',ambientColor:'current_ambience',tripMinutes:'current_fatigue'
};
// Ordered factors: vehicle experience, company, structure, driving style, warmth
// preference. Current SOC, temperature and warnings never rewrite these traits.
const WEIGHTS={
 initiative:[-.35,.8,-.25,.55,.1],expressiveness:[-.45,.65,-.45,.65,.2],
 disclosure:[-.2,.8,-.1,.2,.15],social_appetite:[-.15,1,-.1,.2,.1],
 curiosity:[-.55,.2,-.5,.6,.1],abstraction:[-.2,.1,-.35,.35,.1],
 analytical:[.6,-.25,.65,-.25,-.1],empathy:[.1,.7,.1,-.1,.4],
 planning:[.4,.1,.9,-.45,.05],flexibility:[-.3,.1,-.75,.6,.1],
 playfulness:[-.65,.55,-.5,.7,.15],directness:[.4,-.45,.3,.45,-.2],
 warmth:[-.15,.85,-.1,-.15,.55],autonomy:[.7,-.65,.2,.4,-.1],
 reactivity:[-.5,.3,-.45,.65,.1],recovery:[.15,.2,.3,.25,.1]
};
export function bootstrapFromObserved(observed={},now=Date.now()){
 const get=k=>observed[k]?.value;
 const factors={},evidence=[];
 const add=(name,value,signals)=>{factors[name]=value;evidence.push(...signals.map(signal=>({signal,value:get(signal),role:INPUT_ROLES[signal]})));};
 const km=get('odometerKm'),year=get('modelYear');
 const body=[];if(finite(km)&&km>=0)body.push(clamp(Math.log1p(km/50000)/Math.log(21)));
 const currentYear=new Date(now+9*3600000).getUTCFullYear();
 if(finite(year)&&Number.isInteger(year)&&year>=1950&&year<=currentYear)body.push(clamp((currentYear-year)/20));
 if(body.length)add('experience',body.reduce((a,b)=>a+b)/body.length,[...(finite(km)?['odometerKm']:[]),...(finite(year)&&year<=currentYear?['modelYear']:[])]);
 if(typeof get('passenger')==='boolean')add('company',get('passenger')?.85:.15,['passenger']);
 const cruise=cruiseActive(get('cruiseControlState')),auto=get('hvacAuto'),structure=[];
 if(cruise!==null)structure.push(cruise?.85:.25);if(typeof auto==='boolean')structure.push(auto?.8:.3);
 if(structure.length)add('structure',structure.reduce((a,b)=>a+b)/structure.length,[...(cruise!==null?['cruiseControlState']:[]),...(typeof auto==='boolean'?['hvacAuto']:[])]);
 const mode=String(get('drivingMode')||'').toUpperCase();
 const style={SPORT:.9,SPORT_PLUS:1,ECO:.15,ECO_PLUS:.1,NORMAL:.5,COMFORT:.35,SMART:.6,SNOW:.2}[mode];
 if(finite(style))add('driving_style',style,['drivingMode']);
 const target=get('targetCabinC');
 if(finite(target)&&auto===false)add('warmth_preference',clamp((target-18)/10),['targetCabinC']);
 const keys=['experience','company','structure','driving_style','warmth_preference'];
 const vector=keys.map(k=>finite(factors[k])?2*factors[k]-1:0),traits={},confidence={};
 for(const [name,weights]of Object.entries(WEIGHTS)){
  const score=weights.reduce((sum,w,i)=>sum+w*vector[i],0);
  traits[name]=1/(1+Math.exp(-1.7*score));
  confidence[name]=weights.reduce((sum,w,i)=>sum+(finite(factors[keys[i]])?Math.abs(w):0),0)/weights.reduce((sum,w)=>sum+Math.abs(w),0);
 }
 return {traits,confidence,factors,evidence,available:evidence.length>0};
}
