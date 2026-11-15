import test from 'node:test';
import assert from 'node:assert/strict';
import {scorePersona,testDemoPresets,DEMO_PRESETS,WEIGHTS,KEYS,normalizeInputs} from './scoring.mjs';
test('two complete synthetic scenarios have opposite context-based characters',()=>{
 const r=testDemoPresets();assert.equal(r.lively_weekend_drive.mbti,'ENFP');assert.equal(r.quiet_night_cruise.mbti,'ISTJ');
 assert.ok(r.lively_weekend_drive.scores.E_score>70);assert.ok(r.quiet_night_cruise.scores.E_score<30);
});
test('quiet occupied car stays I; dynamic solo car stays E',()=>{
 const quiet=scorePersona({...DEMO_PRESETS.quiet_night_cruise,passengerCount:4});
 const solo=scorePersona({...DEMO_PRESETS.lively_weekend_drive,passengerCount:0});
 assert.equal(quiet.mbti[0],'I');assert.equal(solo.mbti[0],'E');
});
test('no single sensor, including occupancy, can initialize a personality',()=>{
 for(const input of [{passenger:true},{passenger:false},{averageSpeedKmh:120},{mediaVolumePercent:100},{energyPercent:0},{cruiseControlState:'ENABLED'}]){
  const r=scorePersona(input);assert.equal(r.ready,false);assert.equal(r.mbti,'XXXX');
 }
});
test('normalization is bounded, explicit zero is valid, missing values remain null',()=>{
 const n=normalizeInputs({passengerCount:0,averageSpeedKmh:0,mediaVolumePercent:0,energyPercent:0});
 for(const key of ['passengers','speed','media','energy'])assert.equal(n[key],0);
 const bad=normalizeInputs({energyPercent:101,averageSpeedKmh:NaN,mediaVolumePercent:-1,drivingMode:'UNSUPPORTED'});
 for(const key of ['energy','speed','media','mode'])assert.equal(bad[key],null);
 for(const p of Object.values(DEMO_PRESETS))for(const value of Object.values(normalizeInputs(p)))assert.ok(value===null||value>=0&&value<=100);
});
test('fixed weight budgets cap each input and do not renormalize around missing data',()=>{
 for(const [name,row] of Object.entries(WEIGHTS)){
  assert.equal(row.length,KEYS.length);assert.ok(row.reduce((s,w)=>s+Math.abs(w),0)<=1.000001,name);
  assert.ok(Math.max(...row.map(Math.abs))<=.20,name);
 }
 const a=scorePersona(DEMO_PRESETS.quiet_night_cruise),b=scorePersona({...DEMO_PRESETS.quiet_night_cruise,passengerCount:0});
 assert.ok(Math.abs(a.scores.E_score-b.scores.E_score)<=16);
});
test('contributions reproduce every trait score and preserve deterministic outputs',()=>{
 const r=scorePersona(DEMO_PRESETS.lively_weekend_drive);
 assert.deepEqual(r,scorePersona(DEMO_PRESETS.lively_weekend_drive));
 for(const [name,terms] of Object.entries(r.contributions)){
  const expected=Math.max(6,Math.min(94,50+Object.values(terms).reduce((a,b)=>a+b,0)))/100;
  assert.ok(Math.abs(r.traits[name]-expected)<1e-12);
 }
 assert.ok(Math.abs(r.scores.E_score+r.scores.I_score-100)<1e-12);
});
