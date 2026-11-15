import {pathToFileURL} from 'node:url';

export const SCORING_VERSION = 'weighted-context.v1.1';
export const MIN_GROUPS = 4;
export const KEYS = ['passengers','speed','acceleration','braking','time','comfort','media','cruise','mode','energy'];
export const TRAIT_NAMES = ['initiative','expressiveness','disclosure','social_appetite','curiosity','abstraction','analytical','empathy','planning','flexibility','playfulness','directness','warmth','autonomy','reactivity','recovery'];
const finite = x => typeof x === 'number' && Number.isFinite(x);
const clamp = (x, lo=0, hi=100) => Math.max(lo, Math.min(hi, x));
const valid = (x, lo, hi) => finite(x) && x >= lo && x <= hi;
const scale = (x, lo, hi, physicalMax=hi) => valid(x, lo, physicalMax) ? clamp((x-lo)*100/(hi-lo)) : null;
const mean = xs => xs.length ? xs.reduce((a,b)=>a+b,0)/xs.length : null;

export function cruiseActive(value) {
 // Actual Vehicle SDK 2.3.2 CruiseControl enum names. ENABLED means
 // available/standby; ACTIVATED is the engaged state. Other providers retain
 // their explicit ON/OFF aliases without being presented as console buttons.
 const states = {OTHER:null,ENABLED:false,ACTIVATED:true,USER_OVERRIDE:false,SUSPENDED:false,FORCED_DEACTIVATION_WARNING:null,
  ACTIVE:true,ENGAGED:true,ON:true,RUNNING:true,INACTIVE:false,DISABLED:false,OFF:false,STANDBY:false,CANCELLED:false,CANCELED:false};
 return typeof value === 'string' ? states[value.trim().toUpperCase()] ?? null : null;
}

// Engineering coefficients for a fictional vehicle character. No coefficient
// claims to measure the driver's psychology. Absolute row sums stay <= 1;
// missing inputs retain neutral contributions rather than amplifying one input.
const E = [.10,.16,.14,.10,.12,-.04,.16,-.12,.04,.02];
const N = [.04,.08,.14,.10,.18,-.04,.12,-.14,.14,.02];
const T = [-.10,.06,-.10,-.08,-.10,-.10,-.12,.18,-.10,.06];
const J = [.04,-.08,-.16,-.12,-.12,.06,-.10,.18,-.10,.04];
const negative = row => row.map(x=>-x);
export const WEIGHTS = Object.freeze({
 initiative:E,
 expressiveness:[.08,.12,.16,.10,.12,-.04,.18,-.10,.06,.04],
 disclosure:[.14,.06,.08,.06,.12,.10,.16,-.10,.06,.04],
 social_appetite:[.16,.10,.08,.06,.14,.06,.18,-.08,.06,.02],
 curiosity:N,
 abstraction:[.02,.06,.12,.10,.20,-.04,.16,-.12,.14,.04],
 analytical:T,
 empathy:negative(T),
 planning:J,
 flexibility:negative(J),
 playfulness:[.08,.10,.16,.10,.14,-.04,.18,-.12,.06,.02],
 directness:[-.08,.14,.16,.12,.04,-.06,.12,.06,.10,.04],
 warmth:[.16,-.04,-.08,-.08,.08,.16,.04,-.04,.04,.08],
 autonomy:[-.12,.16,.10,.08,.08,-.04,.10,.06,.10,.04],
 reactivity:[.06,.10,.18,.14,.10,-.04,.18,-.10,.06,.02],
 recovery:[.04,-.06,-.06,-.06,.08,.18,-.04,.08,-.04,.18]
});
const GROUPS = {passengers:'occupancy',speed:'pace',acceleration:'dynamics',braking:'dynamics',time:'time',comfort:'climate',media:'media',cruise:'cruise',mode:'mode',energy:'energy'};

export function normalizeInputs(input={}) {
 const hour = valid(input.hourLocal,0,23.999999) ? input.hourLocal : null;
 const daylight = hour === null ? null : 50 + 50*Math.cos((hour-14)*Math.PI/12);
 const weekend = typeof input.weekend === 'boolean' ? Number(input.weekend)*100 : null;
 const time = mean([daylight,weekend].filter(finite));
 const cabin = valid(input.cabinTemperatureC,-40,80) ? input.cabinTemperatureC : null;
 const target = valid(input.targetTemperatureC,10,40) ? input.targetTemperatureC : 22;
 const comfort = cabin === null ? null : 100-clamp(Math.abs(cabin-target)/12*100);
 const modeValues = {ECO:0,ECO_PLUS:0,COMFORT:25,NORMAL:50,SMART:65,SPORT:90,SPORT_PLUS:100,SNOW:10};
 const mode = typeof input.drivingMode === 'string' ? modeValues[input.drivingMode.toUpperCase()] ?? null : null;
 const cruise = cruiseActive(input.cruiseControlState);
 // The Boolean fallback is presence, not an invented passenger count.
 const passengers = valid(input.passengerCount,0,8) ? scale(input.passengerCount,0,4,8)
  : typeof input.passenger === 'boolean' ? Number(input.passenger)*100 : null;
 return {
  passengers,
  speed:scale(input.averageSpeedKmh,0,120,250),
  acceleration:scale(input.harshAccelPer100Km,0,12,1000),
  braking:scale(input.harshBrakePer100Km,0,12,1000),
  time,comfort,
  media:scale(input.mediaVolumePercent,0,100),
  cruise:cruise === null ? null : Number(cruise)*100,
  mode,energy:scale(input.energyPercent,0,100)
 };
}

export function projectAxes(traits) {
 return [
  .35*traits.initiative+.30*traits.social_appetite+.20*traits.disclosure+.15*traits.expressiveness,
  .65*traits.abstraction+.35*traits.curiosity,
  .5+.5*(traits.analytical-traits.empathy),
  .5+.5*(traits.planning-traits.flexibility)
 ];
}

export function scorePersona(input={}, options={}) {
 const normalized=normalizeInputs(input);
 const present=KEYS.filter(k=>finite(normalized[k]));
 const groups=[...new Set(present.map(k=>GROUPS[k]))];
 const ready=groups.length>=MIN_GROUPS;
 const contributions={},traits={},confidence={};
 for(const name of TRAIT_NAMES) {
  const weights=WEIGHTS[name];
  const terms=Object.fromEntries(KEYS.map((key,i)=>[key,finite(normalized[key])?weights[i]*(normalized[key]-50):0]));
  const delta=Object.values(terms).reduce((a,b)=>a+b,0);
  // A fixed denominator prevents one available sensor becoming the whole model.
  traits[name]=(ready?clamp(50+delta,6,94):50)/100;
  confidence[name]=present.reduce((sum,key)=>sum+Math.abs(weights[KEYS.indexOf(key)]),0)/weights.reduce((sum,w)=>sum+Math.abs(w),0);
  contributions[name]=terms;
 }
 const axes=projectAxes(traits);
 const positive=['E','N','T','J'],negative=['I','S','F','P'];
 const labels=axes.map((value,i)=>{
  if(!ready)return 'X';
  const prior=options.previousMbti?.[i];
  if(Math.abs(value-.5)<.04&&prior&&prior!=='X')return prior;
  return value>=.5?positive[i]:negative[i];
 });
 return {
  algorithm:SCORING_VERSION,ready,groups,normalized,traits,axes,confidence,
  scores:{E_score:axes[0]*100,I_score:(1-axes[0])*100,N_score:axes[1]*100,S_score:(1-axes[1])*100,T_score:axes[2]*100,F_score:(1-axes[2])*100,J_score:axes[3]*100,P_score:(1-axes[3])*100},
  mbti:labels.join(''),contributions,
  missing:KEYS.filter(k=>!finite(normalized[k])),
  provenance:input.source??'caller_supplied'
 };
}

// Two isolated demonstration datasets. Never inserted into a real vehicle's
// history. Optional speed/dynamics/media fields are not claimed as live SDK data.
export const DEMO_PRESETS = Object.freeze({
 lively_weekend_drive:{source:'synthetic',passengerCount:0,averageSpeedKmh:90,harshAccelPer100Km:10,harshBrakePer100Km:8,hourLocal:14,weekend:true,cabinTemperatureC:24,targetTemperatureC:22,mediaVolumePercent:90,cruiseControlState:'ENABLED',drivingMode:'SPORT',energyPercent:85},
 quiet_night_cruise:{source:'synthetic',passengerCount:3,averageSpeedKmh:55,harshAccelPer100Km:.1,harshBrakePer100Km:.2,hourLocal:2,weekend:false,cabinTemperatureC:22,targetTemperatureC:22,mediaVolumePercent:5,cruiseControlState:'ACTIVATED',drivingMode:'ECO',energyPercent:75}
});

export function testDemoPresets() {
 const cases=Object.fromEntries(Object.entries(DEMO_PRESETS).map(([name,input])=>[name,scorePersona(input)]));
 const lively=cases.lively_weekend_drive,quiet=cases.quiet_night_cruise;
 if(!(lively.scores.E_score>58&&quiet.scores.E_score<42))throw Error('MULTIVARIATE_DEMO_FAILED');
 if(lively.provenance!=='synthetic'||quiet.provenance!=='synthetic')throw Error('DEMO_SOURCE_MISSING');
 if(scorePersona({passenger:true}).mbti!=='XXXX')throw Error('SINGLE_SENSOR_MUST_NOT_DECIDE_PERSONALITY');
 return cases;
}

if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) {
 console.log(JSON.stringify(testDemoPresets(),null,2));
}
