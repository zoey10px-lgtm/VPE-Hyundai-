import test from 'node:test';
import assert from 'node:assert/strict';
import {initialProfile,makePlan} from './engine.mjs';
import {packetFor} from './context.mjs';
import {responseIssues,generateCharacterReply} from './generation.mjs';
const p=initialProfile('test'),context={state:{parked:true},userTurn:true},packet=packetFor(p,makePlan(p,context,{happy:1}),context,'turn-1');
test('review identifies copied examples, excess questions and unsupported experience',()=>{
 const bad=packet.performance.examples[0].reply+' 바다는 익숙하니까. 어디? 언제?';
 const issues=responseIssues(bad,packet);assert.ok(issues.length>=3);
});
test('a failing generated candidate is rewritten rather than clipped or shown as a final answer',async()=>{
 let n=0;const result=await generateCharacterReply(async()=>++n===1?'바다는 익숙하니까. 어디? 언제?':'바다라. 한적한 쪽이면 좋겠네.', '바다 보러 가자',packet,[]);
 assert.equal(n,2);assert.equal(result.quality.repairs,1);assert.deepEqual(result.quality.remainingIssues,[]);
});
