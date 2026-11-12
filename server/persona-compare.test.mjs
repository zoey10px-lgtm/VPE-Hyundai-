import test from "node:test";
import assert from "node:assert/strict";
import {contrastPersona,COMPARISON_QUESTION} from "./persona-compare.mjs";

const context={
  mbti:"ENFJ", axes:[.82,.71,.22,.64],
  traits:{activity:.78,curiosity:.81,warmth:.66,confidence:.74,
    caution:.31,playfulness:.87,expressiveness:.80},
  vehicle:{energyPercent:42,actualCabinC:19.1,targetCabinC:29.5},
  affect:{cold:1.0,hunger:0.0},memory:{sharedKm:150}
};

test("comparison reverses all 7 traits and four axes, keeping sensor facts",()=>{
  const original=structuredClone(context);
  const {actual,contrasted}=contrastPersona(context);
  assert.deepEqual(actual,context);
  assert.deepEqual(context,original);
  assert.equal(contrasted.mbti,"ISTP"); // ENFJ => ISTP
  for (const name of Object.keys(context.traits))
    assert.equal(contrasted.traits[name],1-context.traits[name]);
  for (let i=0;i<4;i++)
    assert.equal(contrasted.axes[i],1-context.axes[i]);
  assert.deepEqual(contrasted.vehicle,context.vehicle);
  assert.deepEqual(contrasted.affect,context.affect);
  assert.deepEqual(contrasted.memory,context.memory);
});
test("rejects incomplete personality instead of fabricating fields",()=>{
  assert.throws(()=>contrastPersona({...context,traits:{activity:.2}}));
  assert.throws(()=>contrastPersona({...context,axes:[.2]}));
  assert.ok(COMPARISON_QUESTION.includes("개성"));
});
