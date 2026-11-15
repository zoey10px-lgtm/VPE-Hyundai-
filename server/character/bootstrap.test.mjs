import test from 'node:test';
import assert from 'node:assert/strict';
import {bootstrapFromObserved,cruiseActive,INPUT_ROLES} from './bootstrap.mjs';
import {initialProfile,updateTraits,makePlan,migrateProfile,NAMES} from './engine.mjs';
import {ingest,aggregate,normalizeEvents,SIGNALS,freshState} from './features.mjs';
import {packetFor,compile,report} from './context.mjs';
const now=Date.UTC(2026,10,15,2),day=86400000;
function observe(values,at=now,state={source:'real',profile:initialProfile('same-car')}){
 const events=Object.entries(values).map(([signal,value])=>({id:signal+'-'+at+'-'+value,signal,value,at,source:'emulator'}));
 state=ingest(state,events,at).state;
 const snapshot=aggregate(state.trips,[],at);snapshot.bootstrap=bootstrapFromObserved(state.observed,at);
 state.profile=updateTraits(state.profile,snapshot,at);return state;
}
test('reported zero-km emulator produces a complete SDK initial character without invented trips',()=>{
 const state=observe({odometerKm:0,energyPercent:100,actualCabinC:19.1,cruiseControlState:'ENABLED',passenger:false,parked:true});
 const r=report(state.profile);assert.equal(state.trips.length,0);assert.equal(r.formation,'sdk_initial');
 assert.equal(r.algorithm,'character.v2.1');assert.equal(Object.keys(r.traits).length,16);
 assert.ok(r.axes.some(x=>Math.abs(x-.5)>.02));assert.ok(r.keywords.length>0);assert.match(r.mbti,/^[EINSFTJP]{4}$/);
 assert.ok(Object.values(r.confidence).some(x=>x>0));
 assert.deepEqual(r.initialEvidence.map(x=>x.signal).sort(),['cruiseControlState','odometerKm','passenger']);
});
test('same car and zero trip history changes character immediately when SDK settings change',()=>{
 const a=observe({odometerKm:0,passenger:false,cruiseControlState:'ENABLED',drivingMode:'ECO',hvacAuto:true});
 const b=observe({passenger:true,cruiseControlState:'DISABLED',drivingMode:'SPORT',hvacAuto:false,targetCabinC:26},now+1000,a);
 const plain=observe({odometerKm:0,passenger:false,cruiseControlState:'ENABLED',drivingMode:'ECO',hvacAuto:true});
 assert.ok(b.profile.traits.warmth>plain.profile.traits.warmth);
 assert.ok(b.profile.traits.playfulness>plain.profile.traits.playfulness);
 assert.ok(b.profile.traits.planning<plain.profile.traits.planning);
 const context={state:{parked:true},userTurn:true};
 const ca=compile(packetFor(plain.profile,makePlan(plain.profile,context,{},now),context,'same-turn'));
 const cb=compile(packetFor(b.profile,makePlan(b.profile,context,{},now),context,'same-turn'));
 assert.notEqual(ca.coreHash,cb.coreHash);
 for(const k of NAMES)assert.ok(cb.instructions.includes(k+'='+b.profile.traits[k].toFixed(3)));
 assert.ok(cb.instructions.includes('cruiseControlState'));assert.ok(cb.instructions.includes('SDK'));
});
test('SOC, temperature and warning change current reactions without rewriting initial personality',()=>{
 const a=observe({odometerKm:0,passenger:false}),before=structuredClone(a.profile);
 const b=observe({energyPercent:5,actualCabinC:10,washerFluidWarning:true},now+1000,a);
 assert.deepEqual(b.profile.traits,before.traits);
 const context={state:freshState(b.observed,now+1000),userTurn:true};
 const plan=makePlan(b.profile,context,{},now+1000);
 assert.ok(plan.state.hunger>.8);assert.equal(plan.state.cold,1);
 const packet=packetFor(b.profile,plan,context,'same-turn');
 assert.ok(packet.facts.some(x=>x.signal==='washerFluidWarning'&&x.value===true));
});
test('hundreds of repeated sensor polls never reinforce, reroll or saturate the initial character',()=>{
 let state=observe({odometerKm:0,passenger:true,cruiseControlState:'ENABLED'}),before=structuredClone(state.profile);
 for(let i=1;i<=300;i++)state=observe({odometerKm:0,passenger:true,cruiseControlState:'ENABLED'},now+i*15000,state);
 assert.deepEqual(state.profile.traits,before.traits);assert.equal(state.profile.version,before.version);assert.equal(state.trips.length,0);
});
test('missing SDK data stays missing and unknown enums do not supply fake character evidence',()=>{
 const state=observe({energyPercent:100,actualCabinC:19.1,cruiseControlState:'UNSUPPORTED',drivingMode:'UNSUPPORTED'});
 assert.equal(state.profile.formation,'awaiting_evidence');assert.deepEqual(report(state.profile).keywords,[]);
 assert.equal(bootstrapFromObserved({}).available,false);assert.equal(cruiseActive('UNKNOWN'),null);
 assert.equal(cruiseActive('ENABLED'),true);assert.equal(cruiseActive('DISABLED'),false);
});
test('v2.0 neutral saved profile migrates on stored SDK evidence without losing shared memories',()=>{
 const old=initialProfile('old');old.algorithm='character.v2.0';old.memories=[{id:'memory-1',text:'함께 나눈 이야기'}];
 const snapshot=aggregate([],[],now);snapshot.bootstrap=bootstrapFromObserved({odometerKm:{value:0},passenger:{value:true}},now);
 const next=migrateProfile(old,snapshot,now);assert.equal(next.formation,'sdk_initial');assert.deepEqual(next.memories,old.memories);
 const formed={...old,formation:'formed',lastDay:'2026-11-15',traits:{...old.traits,playfulness:.8}};
 assert.equal(migrateProfile(formed,snapshot,now).traits.playfulness,.8);
});
test('every accepted observation has an explicit role; raw units reach chat as raw facts',()=>{
 for(const key of Object.keys(SIGNALS))assert.ok(INPUT_ROLES[key],key);
 const values={odometerKm:0,modelYear:2024,hvacOn:true,charging:false,washerFluidWarning:false,engineOilState:'NORMAL',brakeFluidState:'NORMAL',batteryCapacityRaw:'77400.0',rangeRemainingRaw:'400.0',cruiseTargetSpeedRaw:'13.8889',tripDistanceRaw:'0.0',tripTimeRaw:'0'};
 const events=Object.entries(values).map(([signal,value])=>({id:signal,signal,value,at:now,source:'real'}));
 assert.equal(normalizeEvents(events,now).length,events.length);
 const state=observe(values),context={state:freshState(state.observed,now),userTurn:true};
 const packet=packetFor(state.profile,makePlan(state.profile,context,{},now),context,'raw-turn');
 for(const signal of Object.keys(values))assert.ok(packet.facts.some(x=>x.signal===signal),signal);
 assert.ok(!state.profile.bootstrap.evidence.some(x=>x.signal.endsWith('Raw')));
});
test('mature experience evolves a bootstrapped character within the daily bound',()=>{
 const state=observe({odometerKm:0,passenger:true}),snapshot={global:{F01:{value:1,q:1},F02:{value:1,q:1},F04:{value:1,q:1}},watermark:'mature-1'};
 const first=updateTraits(state.profile,snapshot,now);assert.equal(first.formation,'formed');
 const next=updateTraits(first,{...snapshot,global:{F01:{value:0,q:1}},watermark:'mature-2'},now+day);
 for(const k of NAMES)assert.ok(Math.abs(next.traits[k]-first.traits[k])<=.015000001);
});
