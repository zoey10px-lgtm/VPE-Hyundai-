import test from "node:test";
import assert from "node:assert/strict";
import {vehiclePersonaProfile} from "./vehicle-persona-policy.mjs";
const base={
  voicePresentation:"남성형",axes:[.71,.55,.85,.69],
  traits:{activity:.78,curiosity:.52,warmth:.31,confidence:.86,caution:.7,playfulness:.67,expressiveness:.79},
  affect:{tension:.1}
};
test("Male presentation never silently uses universal female voice marin",()=>{
  const p=vehiclePersonaProfile(base);
  assert.equal(p.tone,"남성형");
  assert.notEqual(p.voiceName,"marin");
  assert.ok(p.speechSpeed>=.78 && p.speechSpeed<=1.23);
});
test("Female presentation chooses a different primary timbre",()=>{
  const p=vehiclePersonaProfile({...base,voicePresentation:"여성형"});
  assert.equal(p.tone,"여성형");
  assert.notEqual(p.voiceName,vehiclePersonaProfile(base).voiceName);
});
test("T and F prompt policies have different conversation priorities",()=>{
  const t=vehiclePersonaProfile(base);
  const f=vehiclePersonaProfile({...base,axes:[.22,.55,.12,.24],traits:{
    ...base.traits,warmth:.91,activity:.23,confidence:.3,expressiveness:.25}});
  assert.equal(t.type,"T");assert.equal(f.type,"F");
  assert.match(t.instructions,/원인과 실행 가능한 해결부터/);
  assert.match(f.instructions,/상대 감정을 제일 먼저/);
  assert.ok(t.speechSpeed>f.speechSpeed);
});
test("No arbitrary gender is inferred from MBTI and style has bounded speeds",()=>{
  assert.throws(()=>vehiclePersonaProfile({axes:[.5,.4]}));
  const p=vehiclePersonaProfile({...base,axes:[.99,.99,.99,.99]});
  assert.ok(p.speechSpeed<=1.23);
});
