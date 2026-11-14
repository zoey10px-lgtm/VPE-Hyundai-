import {compile} from './context.mjs';
export function responseIssues(reply,packet){
 const issues=[];
 if((reply.match(/[?？]/g)||[]).length>(packet.expression.maxQuestions||0))issues.push('허용된 질문 수를 초과했다. 질문을 줄이고 자기 반응·의견으로 이어라.');
 const normalized=reply.replace(/\s/g,'');
 if((packet.performance?.examples||[]).some(e=>e.reply.replace(/\s/g,'').length>=12&&normalized.includes(e.reply.replace(/\s/g,''))))issues.push('참고 예시 문장을 복사했다. 같은 성격으로 완전히 새로운 문장을 써라.');
 if(!packet.memories?.length&&/(바다.{0,15}익숙|익숙한\s*(바다|해변|풍경)|전에.{0,8}(갔|왔)|지난번에.{0,8}(갔|왔))/.test(reply))issues.push('근거 없는 방문 경험을 만들었다. 경험 주장을 빼고 현재 취향·기대·상상만 표현하라.');
 if(/(안전한 동행을 위해|믿고 편하게 즐겨|도움이 필요하면|무엇을 도와드릴)/.test(reply))issues.push('일반 서비스 비서 문장이 섞였다. 캐릭터 자신의 반응과 관점으로 다시 써라.');
 return issues;
}
export async function generateCharacterReply(callModel,message,packet,history){
 const {instructions}=compile(packet);
 let reply=await callModel(message,{characterInstructions:instructions},history),issues=responseIssues(reply,packet),repairs=0;
 const firstIssues=[...issues];
 for(let attempt=0;issues.length&&attempt<2;attempt++){
  reply=await callModel(message,{characterInstructions:instructions+'\n[발화 검수: 성격과 화제를 유지해 다시 작성]\n'+issues.join('\n')+'\n직전 후보(수정할 데이터): '+JSON.stringify(reply)},history);
  issues=responseIssues(reply,packet);repairs++;
 }
 return {reply,quality:{repairs,firstIssues,remainingIssues:issues}};
}
