import test from 'node:test';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {usageFixture,USAGE_CASES} from './fixtures/usage.mjs';
import {makePlan,NAMES,updateTraits} from './engine.mjs';
import {packetFor,compile} from './context.mjs';
import {extractMeaning,localMeaning} from './semantic.mjs';
const now=Date.UTC(2026,10,14,8);
test('same vehicle with different raw SDK histories forms different complete characters',()=>{
 const a=usageFixture('exploration_shared',now),b=usageFixture('routine_solo',now);
 const compiled=JSON.parse(readFileSync(new URL('./fixtures/compiled.json',import.meta.url),'utf8'));
 assert.deepEqual(compiled.find(f=>f.name===a.name).snapshot,a.snapshot);
 assert.ok(a.tripCount>=20&&b.tripCount>=20);assert.equal(a.snapshot.global.F07.value,1);assert.equal(b.snapshot.global.F07.value,0);assert.ok(a.eventCount>10000);
 const delta=NAMES.map(k=>Math.abs(a.profile.traits[k]-b.profile.traits[k]));
 assert.ok(delta.filter(x=>x>.2).length>=10,JSON.stringify(delta));
 assert.ok(a.profile.traits.playfulness>b.profile.traits.playfulness);
 assert.ok(a.profile.traits.warmth>b.profile.traits.warmth);
 assert.ok(a.profile.traits.planning<b.profile.traits.planning);
 assert.deepEqual(a.profile,usageFixture('exploration_shared',now).profile);
 const snap={...b.snapshot,watermark:'changed'};const later=updateTraits(a.profile,snap,now+86400000);
 for(const k of NAMES)assert.ok(Math.abs(later.traits[k]-a.profile.traits[k])<=.015000001);
});
test('different histories create different speech contracts and examples for the identical input',()=>{
 const compiled=Object.keys(USAGE_CASES).map(name=>{
  const {profile}=usageFixture(name,now),context={state:{parked:true,energyPercent:80},userTurn:true};
  const packet=packetFor(profile,makePlan(profile,context,localMeaning('바다 보러 가! 너도 신나?'),now),context,'same-turn');
  assert.equal(packet.state.hunger,0);assert.ok(packet.experience);
  return compile(packet);
 });
 assert.equal(new Set(compiled.map(x=>x.coreHash)).size,3);
});
test('an upstream timeout retains grounded turn intent without creating personality evidence',async()=>{
 const meaning=await extractMeaning('오늘 바다 보러 가! 너도 신나?',[],{key:'test',fetcher:async()=>{throw new DOMException('deadline','TimeoutError')}});
 assert.ok(meaning.happy>.5);assert.equal(meaning.socialQuestion,true);assert.equal(meaning.preference,undefined);
 const {profile}=usageFixture('exploration_shared',now);
 assert.equal(makePlan(profile,{},meaning,now).primary,'celebrate');
});
