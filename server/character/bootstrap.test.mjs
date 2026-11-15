import test from 'node:test';
import assert from 'node:assert/strict';
import {bootstrapFromObserved,INPUT_ROLES} from './bootstrap.mjs';
import {initialProfile,updateTraits,makePlan,migrateProfile,NAMES,effectiveTraits} from './engine.mjs';
import {ingest,aggregate,SIGNALS,freshState} from './features.mjs';
import {packetFor,compile,report} from './context.mjs';
const now=Date.UTC(2026,10,15,5);
function observe(values,at=now,state={source:'real',profile:initialProfile('same-car')}){
 const events=Object.entries(values).map(([signal,value])=>({id:signal+'-'+at+'-'+value,signal,value,at,source:'emulator'}));
 state=ingest(state,events,at).state;
 const snapshot=aggregate(state.trips,[],at);snapshot.bootstrap=bootstrapFromObserved(state.observed,at);
 state.profile=updateTraits(state.profile,snapshot,at);return state;
}
const base={odometerKm:0,energyPercent:85,actualCabinC:22,passenger:false,cruiseControlState:'ENABLED',drivingMode:'ECO'};
test('zero-km car uses multiple actual inputs, without invented trips',()=>{
 const state=observe(base),r=report(state.profile);
 assert.equal(state.trips.length,0);assert.equal(r.formation,'sdk_initial');assert.equal(r.algorithm,'character.v3.0.1');
 assert.ok(r.scoring.ready);assert.ok(r.scoring.groups.length>=4);assert.equal(Object.keys(r.traits).length,16);
 assert.equal(r.scoring.normalized.media,null);assert.equal(r.scoring.normalized.acceleration,null);
});
test('passenger alone never forms E or I or changes the long-term core',()=>{
 for(const passenger of [true,false]){
  const s=observe({passenger}),r=report(s.profile);
  assert.equal(r.mbti,'XXXX');assert.equal(r.scoring.ready,false);
  assert.deepEqual(s.profile.traits,initialProfile('same-car').traits);
 }
});
test('changes in several observed controls reach the report and same text-chat contract',()=>{
 const a=observe(base),before=structuredClone(a.profile);
 const b=observe({passenger:true,cruiseControlState:'DISABLED',drivingMode:'SPORT'},now+1000,a);
 assert.deepEqual(b.profile.traits,before.traits);
 const ra=report(before),rb=report(b.profile);assert.notDeepEqual(ra.traits,rb.traits);
 const context={state:{parked:true},userTurn:true};
 const pa=packetFor(before,makePlan(before,context,{},now),context,'same');
 const pb=packetFor(b.profile,makePlan(b.profile,context,{},now),context,'same');
 assert.notEqual(compile(pa).coreHash,compile(pb).coreHash);
 for(const k of NAMES)assert.ok(Math.abs(rb.traits[k]-pb.traits[k])<1e-12,k);
});
test('300 identical polls never reinforce the core or contextual weighted score',()=>{
 let s=observe(base),before=structuredClone(s.profile);
 for(let i=1;i<=300;i++)s=observe(base,now+i*10,s);
 assert.deepEqual(s.profile.traits,before.traits);assert.deepEqual(effectiveTraits(s.profile),effectiveTraits(before));
 assert.equal(s.trips.length,0);
});
test('stale contextual sensors expire and raw values retain their provenance',()=>{
 const s=observe({...base,batteryCapacityRaw:'77400.0'});
 const b=bootstrapFromObserved(s.observed,now+31000);
 assert.equal(b.contextScore.ready,false);assert.equal(b.contextScore.normalized.energy,null);
 const context={state:freshState(s.observed,now),userTurn:true};
 const p=packetFor(s.profile,makePlan(s.profile,context,{},now),context,'raw');
 assert.ok(p.facts.some(x=>x.signal==='batteryCapacityRaw'&&x.value==='77400.0'));
});
test('v2.1 direct seat mappings migrate, retaining memories and formed long-term profiles',()=>{
 const s=observe(base),old={...initialProfile('old'),algorithm:'character.v2.1',traits:Object.fromEntries(NAMES.map(k=>[k,.9])),memories:[{id:'kept'}]};
 const snap=aggregate([],[],now);snap.bootstrap=bootstrapFromObserved(s.observed,now);
 const migrated=migrateProfile(old,snap,now);assert.equal(migrated.algorithm,'character.v3.0.1');assert.deepEqual(migrated.memories,old.memories);
 assert.equal(migrated.traits.warmth,.5);
 const formed={...old,formation:'formed',lastDay:'2026-11-15'};
 assert.deepEqual(migrateProfile(formed,snap,now).traits,formed.traits);
});
test('every accepted observation has an explicit input role',()=>{
 for(const key of Object.keys(SIGNALS))assert.ok(INPUT_ROLES[key],key);
});
test('odometer time series derives speed; reset/teleport is never treated as acceleration',()=>{
 let s={source:'real'};
 for(let i=0;i<5;i++)s=ingest(s,[{id:'speed-'+i,signal:'odometerKm',value:100+i*.2,at:now+i*10000,source:'emulator'}],now+i*10000).state;
 assert.ok(Math.abs(s.observed.averageSpeedKmh.value-72)<.00001);
 assert.equal(s.observed.harshAccelPer100Km,undefined);
 s=ingest(s,[{id:'teleport',signal:'odometerKm',value:1000000,at:now+50000,source:'emulator'}],now+50000).state;
 assert.equal(s.observed.averageSpeedKmh,undefined);
});

test('ready report keeps four-letter output while retaining numeric boundary scores',()=>{
 const r=report(observe(base).profile);assert.equal(r.scoring.ready,true);
 assert.match(r.mbti,/^[EINSFTJP]{4}$/);assert.equal(r.mbti,r.axes.map((v,i)=>v>=.5?['E','N','T','J'][i]:['I','S','F','P'][i]).join(''));
});

test('raw SDK evidence refreshes when normalization and personality are unchanged',()=>{
 const a=observe({...base,actualCabinC:22,targetCabinC:22}),before=structuredClone(a.profile);
 const b=observe({actualCabinC:25,targetCabinC:25},now+1000,a);
 assert.deepEqual(effectiveTraits(before),effectiveTraits(b.profile));
 const evidence=report(b.profile).initialEvidence;
 assert.equal(evidence.find(x=>x.signal==='actualCabinC').value,25);
 assert.equal(evidence.find(x=>x.signal==='targetCabinC').value,25);
 assert.equal(evidence.find(x=>x.signal==='actualCabinC').at,now+1000);
});
test('v3.0 upgrade preserves learned traits and memories',()=>{
 const old={...initialProfile('same'),algorithm:'character.v3.0',traits:Object.fromEntries(NAMES.map(k=>[k,.62])),memories:[{id:'retained'}]};
 const next=migrateProfile(old,{global:{},cells:{},watermark:'empty'},now);
 assert.equal(next.algorithm,'character.v3.0.1');assert.deepEqual(next.traits,old.traits);assert.deepEqual(next.memories,old.memories);
});
