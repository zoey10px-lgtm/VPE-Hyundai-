import test from 'node:test';
import assert from 'node:assert/strict';
import {initialProfile,updateTraits,makePlan,NAMES,mbti} from './engine.mjs';
import {normalizeEvents,freshState,aggregate,ingest} from './features.mjs';
import {packetFor,compile} from './context.mjs';
const now=Date.UTC(2026,10,14,8);
test('stable seed, distinct identities and all sixteen bounded dimensions',()=>{
 const a=initialProfile('one');assert.deepEqual(a,initialProfile('one'));assert.notDeepEqual(a.traits,initialProfile('two').traits);
 assert.equal(Object.keys(a.traits).length,16);for(const x of Object.values(a.traits))assert.ok(x>=.25&&x<=.75);
});
test('missing and short histories do not fabricate personality evidence',()=>{
 const p=initialProfile('a');assert.deepEqual(updateTraits(p,aggregate([],[],now),now),p);
 assert.deepEqual(aggregate([{id:'short',end:now,km:.2,minutes:1,novelty:1}],[],now).global,{});
});
test('daily update cap and replay watermark prevent repeated reinforcement',()=>{
 const p=initialProfile('a'),snapshot={global:{F01:{value:1,q:1},F02:{value:1,q:1},F04:{value:1,q:1},F12:{value:1,q:1}},watermark:'new'};
 const next=updateTraits(p,snapshot,now);assert.ok(next.traits.curiosity>p.traits.curiosity);
 for(const k of NAMES)assert.ok(Math.abs(next.traits[k]-p.traits[k])<=.015000001);
 assert.deepEqual(updateTraits(next,{...snapshot,watermark:'other'},now+1000),next);
 assert.deepEqual(updateTraits(next,snapshot,now+86400000),next);
});
test('cold and energy are transient; they never mutate the long term profile',()=>{
 const p=initialProfile('a'),copy=structuredClone(p);
 const plan=makePlan(p,{state:{energyPercent:5,actualCabinC:10,targetCabinC:24}}, {},now);
 assert.ok(plan.state.hunger>.8);assert.equal(plan.state.cold,1);assert.deepEqual(p,copy);
 assert.equal(makePlan(p,{state:{}},{},now).state.hunger,null);
});
test('stale observations expire; invalid values and test data cannot pollute real history',()=>{
 assert.throws(()=>normalizeEvents([{id:'x',signal:'energyPercent',value:200,at:now,source:'real'}],now));
 assert.equal(freshState({energyPercent:{value:5,at:now-31000}},now).energyPercent,null);
 const result=ingest({source:'real'},[{id:'x',signal:'odometerKm',value:1,at:now,source:'test'}],now);
 assert.deepEqual(result.state.observed,{});
});
test('questions get answers, distress suppresses teasing and detail expands turn budget',()=>{
 const p=initialProfile('a');p.preferences.teasing=true;
 assert.equal(makePlan(p,{}, {question:1},now).primary,'solve');
 const sad=makePlan(p,{}, {tired:1,play:1},now);assert.equal(sad.scores.tease,undefined);
 const detailed=makePlan(p,{state:{parked:true}},{detail:1,question:1},now);
 assert.equal(detailed.expression.maxSentences,10);
});
test('compiler sends actual dimensions, actions and evidence; MBTI is not the control',()=>{
 const p=initialProfile('a'),context={state:{energyPercent:12},userTurn:true};
 const packet=packetFor(p,makePlan(p,context,{},now),context,'turn-1');
 const c=compile(packet);for(const k of NAMES)assert.ok(c.instructions.includes(k+'='));
 assert.ok(c.instructions.includes('energyPercent'));assert.equal(c.coreHash,compile(packet).coreHash);
 p.traits.expressiveness=.99;
 assert.notEqual(c.coreHash,compile(packetFor(p,makePlan(p,context,{},now),context,'turn-1')).coreHash);
});
test('MBTI border remains X rather than forcing a 1:1 type label',()=>{
 const p=Object.fromEntries(NAMES.map(k=>[k,.5]));assert.equal(mbti(p).label,'XXXX');
});
