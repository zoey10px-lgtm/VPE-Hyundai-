/** Allowlisted real vehicle events. This module makes prompts unit-testable. */
export const PROACTIVE_EVENTS=Object.freeze({
  greeting:"차량 서비스가 연결됨. 짧게 인사하고 오늘의 경험을 물어봐.",
  washer:"워셔액 부족 상태가 관측됨. 안전하게 주행을 마친 후 확인을 제안해.",
  critical_energy:"배터리 SOC 10% 이하가 수신됨. 충전 계획 확인을 최우선으로 제안해.",
  low_energy:"배터리 SOC 20% 이하가 수신됨. 충전할 시점을 생각하도록 제안해.",
  hot:"실내 온도가 목표보다 높아 더위 감정이 활성화됨. 냉방 점검을 제안해.",
  cold:"실내 온도가 목표보다 낮아 추위 감정이 활성화됨. 난방 설정을 제안해.",
  charge_switch:"EV 충전 스위치가 켜짐. 이것만으로 실제 충전 전류가 흐른다고 단정할 수 없음."
});
export function validProactiveKind(kind){
  return typeof kind==="string" && Object.hasOwn(PROACTIVE_EVENTS,kind);
}
export function proactivePrompt(kind){
  if(!validProactiveKind(kind))throw new Error("INVALID_PROACTIVE_EVENT");
  return `지금 VPE 차량 자신이 사용자 발화 없이 먼저 말을 건네야 해.
관측된 이벤트: ${PROACTIVE_EVENTS[kind]}
현재의 차량 HPE 장기 7가지 기질과 단기 감정을 참고해서 그 차량만의 말투로 한국어 1~2문장을 만들어.
안전하고 구체적인 제안을 먼저 하고, 자연스럽다면 짧게 한 가지 질문을 붙여서 대화를 시작해.
차량 조작·충전 시작·길 안내를 실행했다고 주장하지 마.
실제 주행속도·도로 정보·실제 충전 전류·운행 이력을 지어내지 마.
사용자가 먼저 질문한 것처럼 대답하지 마. 이벤트: ${kind}`;
}
