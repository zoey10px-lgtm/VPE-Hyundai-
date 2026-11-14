import {ingest,aggregate} from '../features.mjs';
import {initialProfile,updateTraits} from '../engine.mjs';
export const USAGE_CASES={
 exploration_shared:{social:true,novel:true,variable:true,night:false,long:false},
 routine_solo:{social:false,novel:false,variable:false,night:false,long:false},
 night_distance:{social:false,novel:true,variable:false,night:true,long:true}
};
// Isolated synthetic SDK event streams. Never uploaded as real user history.
export function usageFixture(name,now=Date.UTC(2026,10,14,8)){
 const c=USAGE_CASES[name];if(!c)throw Error('UNKNOWN_FIXTURE');
 let state={source:'test'},km=1000,seq=0;
 for(let day=0;day<24;day++){
  const duration=c.long?75:c.variable?[10,35,80,15][day%4]:20;
  const hour=c.night?14:c.variable?[0,5,9,2][day%4]:0;
  const start=now-(25-day)*86400000+hour*3600000;
  const lat=35+(c.novel?day*.03:day%2*.002),lon=128+(c.novel?day*.02:day%2*.002);
  const startKm=km,tripKm=c.long?140:c.variable?[4,55,120,8][day%4]:12;
  for(let step=0;step<=duration*2+11;step++){
   const at=start+step*30000,moving=step<=duration*2;
   km=startKm+tripKm*Math.min(step/(duration*2),1);
   const data={odometerKm:km,energyPercent:80,parked:!moving,passenger:c.social,hvacAuto:false,targetCabinC:c.variable?20+step%5:22,
    latitude:lat+.01*Math.min(step/(duration*2),1),longitude:lon+.01*Math.min(step/(duration*2),1),drivingMode:c.variable?(step%2?'SPORT':'ECO'):'ECO',cruiseControlState:c.long?'ON':'OFF'};
   state=ingest(state,Object.entries(data).map(([signal,value])=>({id:'fixture-'+seq++,signal,value,at,source:'test'})),at).state;
  }
 }
 const snapshot=aggregate(state.trips,[],now,state.refills||[]);
 const profile=updateTraits(initialProfile('same-vehicle'),snapshot,now);
 return {name,profile,snapshot,tripCount:state.trips.length,eventCount:seq};
}
