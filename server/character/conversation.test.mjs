import test from 'node:test';
import assert from 'node:assert/strict';
import {conversationName,conversationVehicle} from './conversation.mjs';
import {initialProfile,makePlan} from './engine.mjs';
import {packetFor,compile,CONVERSATION_VERSION} from './context.mjs';
import {generateCharacterReply,responseIssues} from './generation.mjs';
import {extractMeaning} from './semantic.mjs';
const now=Date.parse('2026-11-15T08:00:00Z');
const event=(value,age=0)=>({value,at:now-age,source:'emulator'});

test('registered name and all observed vehicle data reach both text and voice plans',()=>{
 const observed={energyPercent:event(12),actualCabinC:event(3),targetCabinC:event(22),passenger:event(true),parked:event(true),batteryCapacityRaw:event('77400.0'),cruiseControlState:event('ENABLED'),odometerKm:event(87320,60000),sdkDiagnostic:event('sdk_error')};
 const state=Object.fromEntries(Object.entries(observed).filter(([,e])=>now-e.at<15000).map(([k,e])=>[k,e.value]));
 const p=initialProfile('name-test'),before=JSON.stringify(p.traits);
 const context={state,vehicleName:conversationName(' 삣삐 ','old'),vehicle:conversationVehicle(observed,now),userTurn:true};
 const packet=packetFor(p,makePlan(p,context,{},now),context,'name-turn');packet.latestUserText='삣삐야, 지금 네 상태 어때?';
 assert.equal(packet.identity.name,'삣삐');assert.equal(packet.conversationVersion,CONVERSATION_VERSION);
 assert.equal(packet.vehicle.current.actualCabinC,3);
 assert.equal(packet.vehicle.current.batteryCapacityRaw,'77400.0');
 assert.equal(packet.vehicle.lastKnown.odometerKm.value,87320);
 assert.equal(packet.vehicle.lastKnown.odometerKm.ageSeconds,60);
 assert.equal(packet.vehicle.current.odometerKm,undefined);
 assert.equal(packet.vehicle.current.sdkDiagnostic,undefined);
 assert.ok(packet.state.cold>.9);assert.ok(packet.state.hunger>.5);
 const prompt=compile(packet).instructions;
 for(const text of ['삣삐','너 자신의 이름','vehicle.current','vehicle.lastKnown','얼어죽겠어','77400.0','87320'])assert.ok(prompt.includes(text),text);
 assert.equal(JSON.stringify(p.traits),before,'dialogue context must not change scoring');
});

test('default names and rename precedence stay independent of prior conversation',()=>{
 assert.equal(conversationName('삣삐','old'),'삣삐');
 assert.equal(conversationName(undefined,'삣삐'),'삣삐');
 assert.equal(conversationName('',undefined),'내 차');
 assert.equal(conversationName(undefined,'내 차량'),'내 차');
 assert.equal(conversationName('bad\nname','삣삐'),'삣삐');
});

test('a nickname is not treated as profanity, while actual user questions about profanity remain answerable',async()=>{
 const p=initialProfile('name-test'),context={vehicleName:'삣삐',state:{parked:true},userTurn:true};
 const packet=packetFor(p,makePlan(p,context,{},now),context,'name-turn');packet.latestUserText='삣삐야';
 assert.ok(responseIssues('그런 말은 하지 말고 예쁜 말로 불러 줘.',packet).some(x=>x.includes('이름')));
 let calls=0;const reply=await generateCharacterReply(async()=>++calls===1?'욕설은 쓰지 말아 줘.':'응, 나 불렀어?','삣삐야',packet,[]);
 assert.equal(calls,2);assert.equal(reply.reply,'응, 나 불렀어?');
 packet.latestUserText='삣삐라는 이름이 욕설이야?';
 assert.equal(responseIssues('욕설이 아니라 네가 지어준 내 이름이야.',packet).length,0);
});

test('semantic interpretation also receives the character own name',async()=>{
 let body;
 const meaning=await extractMeaning('삣삐야',[],{key:'test-key',vehicleName:'삣삐',fetcher:async(_url,options)=>{
  body=JSON.parse(options.body);return {ok:true,json:async()=>({output:[{content:[{type:'output_text',text:JSON.stringify({signals:[],preference:null,memory:null})}]}]})};
 }});
 assert.equal(JSON.parse(body.input).vehicleName,'삣삐');
 assert.match(body.instructions,/캐릭터 자신의 등록 이름/);
 assert.equal(meaning.preference,undefined);
});
