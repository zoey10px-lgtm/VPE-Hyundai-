import test from "node:test";
import assert from "node:assert/strict";
import {PROACTIVE_EVENTS,validProactiveKind,proactivePrompt} from "./proactive-events.mjs";
test("seven real event types are explicitly allowed",()=>{
  assert.equal(Object.keys(PROACTIVE_EVENTS).length,7);
  for(const kind of Object.keys(PROACTIVE_EVENTS)){
    assert.ok(validProactiveKind(kind));
    const prompt=proactivePrompt(kind);
    assert.ok(prompt.includes(kind));
    assert.match(prompt,/지어내지 마/);
  }
});
test("arbitrary and inherited keys are rejected",()=>{
  for(const kind of ["__proto__","new_user_message",null,""]){
    assert.equal(validProactiveKind(kind),false);
  }
  assert.throws(()=>proactivePrompt("wrong"));
});
test("charge event never asserts real charging",()=>{
  assert.match(proactivePrompt("charge_switch"),/실제 충전 전류가 흐른다고 단정할 수 없음/);
});
