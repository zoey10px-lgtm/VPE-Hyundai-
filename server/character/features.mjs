import {clamp,finite,hash} from './engine.mjs';
const DAY=86400000;
const median=a=>{a=[...a].sort((a,b)=>a-b);return a.length?(a[Math.floor((a.length-1)/2)]+a[Math.ceil((a.length-1)/2)])/2:null;};
const quant=(a,p)=>{a=[...a].sort((a,b)=>a-b);return a.length?a[Math.floor((a.length-1)*p)]:null;};
const iqr=a=>a.length?quant(a,.75)-quant(a,.25):null;
export function cellOf(t){return typeof t.passenger!=='boolean'?null:`${t.passenger?'passenger':'solo'}-${[0,6].includes(new Date(t.start+9*3600000).getUTCDay())?'weekend':'weekday'}`;}
function feature(rows,key,min=8,now=Date.now()){
 const valid=rows.filter(r=>finite(r[key]));if(valid.length<min)return null;
 const sum=valid.reduce((s,r)=>s+2**(-(now-r.end)/DAY/14),0);
 const value=valid.reduce((s,r)=>s+r[key]*2**(-(now-r.end)/DAY/14),0)/sum;
 return {value:clamp(value),n:valid.length,q:valid.length/Math.max(1,rows.length)*valid.reduce((s,r)=>s+(r.quality??1),0)/valid.length*Math.min(1,valid.length/20),evidence:valid.map(r=>r.id)};
}
export function aggregate(trips,feedback=[],now=Date.now()){
 const rows=trips.filter(t=>t.end>now-28*DAY&&t.end<=now&&t.km>=1&&t.minutes>=3);
 function build(rs){const out={};for(const[f,key,min]of [['F01','novelty',8],['F02','routeNovelty',8],['F05','refillConsistency',4],['F06','settingStability',8],['F07','passengerRatio',8],['F08','nightRatio',8],['F10','modeEntropy',8],['F11','cruiseRatio',8]]){const e=feature(rs,key,min,now);if(e)out[f]=e;}
  const complete=rs.filter(t=>!t.partial);if(complete.length>=8){
   const groups=[complete.filter(t=>![0,6].includes(new Date(t.start+9*3600000).getUTCDay())),complete.filter(t=>[0,6].includes(new Date(t.start+9*3600000).getUTCDay()))];
   const vals=groups.filter(g=>g.length>=4).map(g=>{let x=0,y=0,w=0;for(const t of g){const a=((t.start+9*3600000)%DAY)/DAY*2*Math.PI,wt=2**(-(now-t.end)/DAY/14);x+=wt*Math.cos(a);y+=wt*Math.sin(a);w+=wt;}return {value:Math.hypot(x,y)/w,n:g.length};});
   if(vals.length)out.F03={value:vals.reduce((s,v)=>s+v.value*v.n,0)/vals.reduce((s,v)=>s+v.n,0),n:complete.length,q:Math.min(1,complete.length/20)};
   out.F04={value:clamp(iqr(complete.map(t=>Math.log1p(t.km)))/Math.log(4)),n:complete.length,q:Math.min(1,complete.length/20)};
   out.F09={value:clamp(median(complete.map(t=>t.km))/100),n:complete.length,q:Math.min(1,complete.length/20)};
  }
  return out;}
 const global=build(rows),cells={},cellCounts={};for(const cell of new Set(rows.map(cellOf).filter(Boolean))){const rs=rows.filter(t=>cellOf(t)===cell);cells[cell]=build(rs);cellCounts[cell]=rs.length;}
 const cs=Object.values(cells).map(c=>c.F01?.value).filter(finite);if(rows.length>=12&&cs.length>=2){const mean=cs.reduce((a,b)=>a+b)/cs.length;global.F12={value:1-clamp(Math.sqrt(cs.reduce((s,x)=>s+(x-mean)**2,0)/cs.length)/.5),q:Math.min(1,rows.length/20),n:rows.length};}
 for(let i=1;i<=15;i++){const key='D'+String(i).padStart(2,'0'),rs=feedback.filter(f=>f.feature===key&&f.at>now-28*DAY&&f.confidence>=.8);
  const min=i===1?12:i===4?16:8;if(rs.length>=min)global[key]={value:rs.reduce((s,r)=>s+r.value,0)/rs.length,n:rs.length,q:Math.min(1,rs.length/20)};}
 return {global,cells,cellCounts,watermark:hash([rows.map(r=>r.id),feedback.map(r=>r.id)]),at:now};
}
export const SIGNALS={odometerKm:[0,10000000,30000],energyPercent:[0,100,30000],actualCabinC:[-40,80,30000],targetCabinC:[10,40,30000],parked:null,hvacAuto:null,passenger:null,drivingMode:null,cruiseControlState:null,safetyWarning:null,latitude:[-90,90,30000],longitude:[-180,180,30000],fanSpeedLevel:[0,100,30000],ambientBrightness:[0,100,30000],ambientColor:null,tripMinutes:[0,100000,30000]};
export function normalizeEvents(events,now=Date.now()){
 if(!Array.isArray(events)||events.length>200)throw new Error('INVALID_OBSERVATIONS');
 return events.map(e=>{if(!e||typeof e.id!=='string'||e.id.length>100||!Object.hasOwn(SIGNALS,e.signal)||!finite(e.at)||e.at>now+60000||e.at<now-7*DAY||!['real','emulator','test'].includes(e.source))throw new Error('INVALID_OBSERVATION');
  if(e.valid===false)return {...e,value:null};const bounds=SIGNALS[e.signal];if(bounds&&(!finite(e.value)||e.value<bounds[0]||e.value>bounds[1]))throw new Error('INVALID_SIGNAL_VALUE');
  if(!bounds&&['parked','hvacAuto','passenger','safetyWarning'].includes(e.signal)&&typeof e.value!=='boolean')throw new Error('INVALID_SIGNAL_VALUE');
  if(!bounds&&!['parked','hvacAuto','passenger','safetyWarning'].includes(e.signal)&&(typeof e.value!=='string'||e.value.length>100))throw new Error('INVALID_SIGNAL_VALUE');
  return {id:e.id,signal:e.signal,value:e.value,at:e.at,source:e.source};});
}
export function freshState(observed,now=Date.now()){return Object.fromEntries(Object.entries(observed||{}).map(([k,e])=>[k,now-e.at<=(SIGNALS[k]?.[2]||15000)?e.value:null]));}
export function ingest(state,events,now=Date.now()){
 state=structuredClone(state);state.observed??={};state.seen??=[];state.trips??=[];state.places??=[];
 let closed=false;
 for(const e of normalizeEvents(events,now).sort((a,b)=>a.at-b.at)){
  if(e.source==='test'&&state.source!=='test')continue;
  if(state.seen.includes(e.id)||e.at<(state.observed[e.signal]?.at??0))continue;
  state.seen.push(e.id);state.observed[e.signal]=e;
  const s=freshState(state.observed,e.at);if(!finite(s.odometerKm))continue;
  const anchor=state.anchor;
  if(!anchor){state.anchor={km:s.odometerKm,at:e.at};continue;}
  if(s.odometerKm<anchor.km){state.anchor={km:s.odometerKm,at:e.at};state.active=null;continue;}
  if(!state.active&&s.odometerKm-anchor.km>=.1&&e.at-anchor.at<=60000){state.active={id:'trip-'+hash(anchor).slice(0,24),start:anchor.at,end:e.at,startKm:anchor.km,lastKm:s.odometerKm,lastAt:e.at,samples:[],quality:s.parked===false?1:.7};}
  if(!state.active&&e.at-anchor.at>60000)state.anchor={km:s.odometerKm,at:e.at};
  const t=state.active;if(!t)continue;
  const moved=s.odometerKm>t.lastKm;if(moved){t.lastMove=e.at;t.lastKm=s.odometerKm;state.stoppedAt=null;}
  const gap=e.at-t.lastAt;t.lastAt=e.at;t.end=e.at;
  if(e.at-(t.samples.at(-1)?.at||0)>=10000)t.samples.push({at:e.at,km:s.odometerKm,passenger:s.passenger,mode:s.drivingMode,cruise:s.cruiseControlState,lat:s.latitude,lon:s.longitude,target:s.hvacAuto===false?s.targetCabinC:null,energy:s.energyPercent});
  if(s.parked===true&&!moved)state.stoppedAt??=e.at;else if(moved)state.stoppedAt=null;
  if(gap>15*60000||state.stoppedAt&&e.at-state.stoppedAt>=5*60000){
   t.partial=gap>15*60000;t.end=t.lastMove||t.end;t.km=t.lastKm-t.startKm;t.minutes=(t.end-t.start)/60000;
   const known=t.samples.filter(x=>typeof x.passenger==='boolean');t.passengerRatio=known.length?known.filter(x=>x.passenger).length/known.length:null;t.passenger=t.passengerRatio===null?null:t.passengerRatio>.2;
   const loc=t.samples.filter(x=>finite(x.lat)&&finite(x.lon));const last=loc.at(-1);
   if(last&&!t.partial){const dist=p=>Math.hypot((p.lat-last.lat)*111000,(p.lon-last.lon)*111000*Math.cos(last.lat*Math.PI/180));
    const place=state.places.find(p=>dist(p)<300);if(state.firstAt&&t.start-state.firstAt>=7*DAY)t.novelty=place?0:1;
    if(!place)state.places.push({lat:last.lat,lon:last.lon,at:t.end});}
   const modes=t.samples.map(x=>x.mode).filter(Boolean);if(modes.length){const counts={};modes.forEach(m=>counts[m]=(counts[m]||0)+1);const ps=Object.values(counts).map(n=>n/modes.length);if(ps.length>1)t.modeEntropy=-ps.reduce((s,p)=>s+p*Math.log(p),0)/Math.log(ps.length);}
   const manual=t.samples.map(x=>x.target).filter(finite);
   if(manual.length>=3)t.settingStability=1-clamp((Math.max(...manual)-Math.min(...manual))/8);
   const replenishments=t.samples.slice(1).flatMap((x,i)=>finite(x.energy)&&finite(t.samples[i].energy)&&x.energy-t.samples[i].energy>=5?[t.samples[i].energy]:[]);
   state.refills=[...(state.refills||[]),...replenishments].slice(-40);
   if(replenishments.length&&state.refills.length>=4)t.refillConsistency=1-clamp(iqr(state.refills)/30);
   if(loc.length>=3&&!t.partial){
    const cells=[...new Set(loc.map(x=>`${Math.round(x.lat*1000)},${Math.round(x.lon*1000)}`))];
    const known=new Set(state.routeCells||[]);
    if(state.firstAt&&t.start-state.firstAt>=7*DAY)t.routeNovelty=cells.filter(c=>!known.has(c)).length/cells.length;
    state.routeCells=[...new Set([...known,...cells])].slice(-20000);
   }
   const cc=t.samples.filter(x=>['ACTIVE','ENGAGED','ON','INACTIVE','OFF','STANDBY'].includes(x.cruise));t.cruiseRatio=cc.length?cc.filter(x=>['ACTIVE','ENGAGED','ON'].includes(x.cruise)).length/cc.length:null;
   t.nightRatio=t.samples.length?t.samples.filter(x=>{const h=new Date(x.at+9*3600000).getUTCHours();return h>=22||h<6;}).length/t.samples.length:null;
   state.trips.push(t);state.active=null;state.stoppedAt=null;state.anchor={km:s.odometerKm,at:e.at};closed=true;
  }
 }
 state.firstAt??=events[0]?.at;state.seen=state.seen.slice(-5000);state.trips=state.trips.filter(t=>t.end>now-90*DAY);state.places=state.places.slice(-2000);
 return {state,closed};
}
