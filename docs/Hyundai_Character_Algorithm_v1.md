# 차량 경험 기반 캐릭터 알고리즘 v1 — 구현 명세

작성일: 2026-11-14 (한국시간). 대상: VPE-Hyundai Android + Render. 상태: 구현 기준을 확정한 설계 문서. 현재 앱에 적용하거나 실차·GPT·음성 품질 검증을 완료한 결과가 아니다.

## 0. 이번에 확정한 결정

1. 6종의 고정 캐릭터 선택기를 사용하지 않는다. 차량별 16개 연속 성향, 6개 가치 지향, 환경 취향, 공유 기억, 관계 상태를 유지한다.
2. MBTI는 결과 요약이며 생성 입력·가중치·보이스 선택에 사용하지 않는다.
3. 장기 성향은 서버의 결정적 수치 엔진이 관리한다. GPT는 기록에서 사실·대화 피드백을 추출하고, 성향을 읽어 설명·대사를 생성한다. GPT가 장기 수치를 임의 수정하는 경로는 없다.
4. 차량 기록은 탐험·일상·환경·동행 경험에 연결한다. 유머·논리·감정 선호 등 근거가 약한 성향은 초기 창작 기질과 명시적인 대화 피드백으로만 조정한다.
5. 원시 데이터 → 여정 경험 → 조건부 패턴 → 장기 성향 → 상황 평가 → 행동 후보 점수 → 표현 프로필 → 발화의 경로를 기록한다.
6. 동일한 초기 기질에 다른 경험을 넣는 검증을 필수로 한다. 서로 다른 난수 초기값만으로 만든 차이를 데이터 분석의 성과로 집계하지 않는다.
7. 이번 버전은 설계 계수를 사용한 경험 적응 모델이다. 학습 데이터로 계수를 학습한 모델, 심리학적으로 검증된 인간 성격 추정기라는 표현은 사용하지 않는다.

## 1. 현재 코드에서 확인한 교체 지점

2026-11-14 기본 브랜치의 파일 내용을 읽고 대조했다. 파일 SHA는 각 파일 내용 식별자이다.

| 파일 | 확인 내용 | v1 변경 계약 |
|---|---|---|
| HumanPersonalityEngine.kt, SHA e380001471ecf091779bdb18589546f97f919088 | E/N/T/J를 먼저 산출한 뒤 7기질을 파생. 크루즈→T/J, 대화 횟수→E/F, 모드 다양성→N/F. express에서 고정 문장 선택. excitement/fatigue=0 | 서버의 16성향·상태 결과를 표시하는 구조로 변경. 해당 직접 매핑과 성격용 고정 문장 제거 |
| VehiclePatternTracker.kt, SHA 27e821ba71ce7c56ebde65931c1d238d1242f277 | 모드/크루즈 샘플 수 집계, 대화 횟수 12회에서 비율 포화 | 시간가중 여정 집계 및 실제 대화 기회·수용·거절 분모를 저장 |
| server/vehicle-persona-policy.mjs, SHA 1844b855936a1198ff432b1727ce670f3f7df6fa | T/F·I/E 임계값으로 프롬프트 분기, RESERVED를 수줍음과 연결 | 공통 ContextCompiler로 교체. 차분함·자기표현·직설성·유머를 독립 제어 |
| VpeRealtimeVoice.kt, SHA dd76c448850c657f00e0f49826be82c56d9a4a5a | 처음 지침에 새 JSON을 덧붙이고 처음 말투 유지를 재지시. session.created에서도 ready 처리 | 전체 최신 지침 교체, 버전/해시 확인, 발화 단위 계획 적용, 단일 응답 큐 |
| server/index.mjs, SHA 3feceeed31cd37e807254aab3bbf1a2306ab16bb | Realtime semantic_vad create_response=true. 텍스트/음성 요청 생성 | 자동 응답을 끄고 계획 적용 후 명시 response.create. 두 경로에 같은 패키지 사용 |

기존 음성 PCM 재생·부분 쓰기 처리·사용자 끼어들기 시 truncate 코드는 유지하며 회귀 검증한다. 현재 연결은 Android→Realtime WebSocket이다. WebRTC sideband로 전환하는 작업은 v1에 넣지 않는다.

## 2. 데이터 계약과 수집

### 2.1 Observation

필수 필드: event_id(UUID), vehicle_profile_id, user_profile_id(관계 소유자), source(real/emulator/test), signal, value, unit, area, observed_at, received_at, sequence, validity(valid/unavailable/unsupported/error), sdk_version, capability_version, trip_id(nullable).

- 범위는 SDK capability와 단위 계약으로 검증한다. 확인되지 않은 단위는 raw 전용으로 저장하고 특징 계산에서 제외한다.
- 중복 키는 (profile, event_id), 동일 signal의 역순 이벤트는 이력에 남기되 최신 상태를 덮어쓰지 않는다.
- 동일 물리 사건의 polling과 callback은 signal/area/sequence 또는 값·시각 조합으로 중복 제거한다.
- 기록된 0은 실제 0, 미수신은 null. signal별 지원 상태와 유효 관측률을 별도로 유지한다.
- 테스트 기록은 실제 사용자 프로필 학습에서 분리한다. 동일 모델 검증을 위해 별도의 테스트 프로필에만 투입한다.
- VIN/CRN 원문은 모델 입력에 넣지 않는다. 서버의 차량 프로필 키와 현재 사용자 계정을 분리한다. 좌석 점유만으로 탑승자 신원을 추정하지 않는다.

### 2.2 수집 주기와 유효기간

| 종류 | 수집/전송 | 현재 상태 TTL |
|---|---|---|
| 기어·문·좌석·모드·크루즈·경고 | 지원 시 변경 구독 + 연결 시 초기값, 앱에서 1초 배치 | 마지막 성공 읽기/상태 확인 후 15초 |
| SOC·연료·온도·풍량 | 변경 구독, 10초 상태 확인, 5초 배치 | 30초 |
| 거리·현재 트립 시간 | 10초 조회 | 30초 |
| 조향·제동 | 지원 갱신률로 수신, 10초 통계와 전환 횟수 전송 | 실시간 판단 2초, 장기 집계는 구간 품질 포함 |
| 위치 | 지원 시 5초 요청, 실제 전달률 기록 | 30초 |
| 식별·capability | 연결 시, SDK 환경 변경 시 | 연결 단위 |

SDK가 요청 주기를 보장한다고 가정하지 않는다. stale 값으로 당시 상태를 추정하지 않는다. 상태 변경 구독만 제공하는 경우 성공한 초기값과 연결 건전성 확인을 함께 사용하고, 확인 실패 시 TTL 후 unknown으로 전환한다.

### 2.3 여정 상태기계

- IDLE→ACTIVE: 기어가 D/R로 확인되고 30초 내 유효 이동(거리 증가 또는 위치 이동)이 관측됨. 기어 미지원이면 60초 안에 100m 이상 이동을 관측한 때 ACTIVE, confidence=0.7.
- ACTIVE→STOP_CANDIDATE: P와 2분간 이동 없음. 기어 미지원이면 이동 없음 5분.
- STOP_CANDIDATE→CLOSED: 확인된 주차 5분. 다시 이동하면 같은 여정으로 복귀. 위치·거리 관측이 끊긴 것은 정차로 판정하지 않는다.
- 앱 종료/연결 끊김: 15분 후 partial 종료. 재접속 시 거리의 단조성과 시간 간격으로 15분 이내 구간만 연결. 부분 기록을 완전한 여정으로 처리하지 않는다.
- 최초 트립 카운터 리셋은 구간 경계 후보로만 사용. 누적 계기 리셋·역행은 해당 차분 무효화.
- 기본 학습 자격: 유효 이동거리≥1km, 유효 운행시간≥3분. 그 미만은 현재 상태와 에피소드만 저장. 이는 센서 잡음 억제를 위한 v1 결정이다.
- 출발 시각은 이동 시작. 앱 실행 시각을 출발로 대체하지 않는다. 거리/시간만으로 계산한 구간 평균을 실제 속도 센서값으로 표기하지 않는다.

### 2.4 기존 108종 입력의 용도

주행·에너지·공조·좌석·조명·창문: 아래 특징/취향/상태 계산에 사용. Door/Gear: 여정·탑승 맥락. Brake/Steering/TurnSignal/Wiper: 운전 맥락·구간 묘사, 성격 직접 가중치 0. Safety/Tire: 사실 안내, 장기 성격 가중치 0. VIN/CRN/Display/Capability: 식별·해석, 성격 가중치 0. ETCS: 안내 음량 취향. 전체 함수명은 기존 Hyundai_Persona_Input_Catalog.md를 따른다.

위치·동승·내비게이션은 capability 확인 후 추가 연결하는 채널이다. 데이터가 없으면 해당 특징은 null이며 다른 센서로 대체하지 않는다. 조향각·브레이크 Boolean만으로 위험 운전/급제동/공격성 판단을 만들지 않는다.

## 3. 2차 특징의 정확한 정의

모든 비율은 0..1. 기간은 최근 28일, 시간 가중치 w=2^(-age_days/14). 장기 비교용 90일 요약도 저장하되 v1 성향 갱신에는 28일만 사용한다. 유효 관측이 없는 분모는 null.

표의 min은 갱신을 시작할 최소 독립 여정 또는 독립 대화 기회 수다. 독립 대화 기회는 turn_id와 종류로 중복 제거하고, 동일 종류가 10분 안에 반복되면 한 기회로 묶는다.

| ID | 특징과 계산 | min |
|---|---|---:|
| F01 | 새 목적지 비율: 도착점이 이전에 관측한 장소 중심 300m 밖인 여정/유효 목적지 여정. 초기 7일은 장소 목록 수집만 하고 새로움 학습 제외 | 8 |
| F02 | 경로 새로움: 200m 공간 셀 집합을 과거 동일 출발/도착 장소의 최대 Jaccard 유사도와 비교한 1-similarity. 비교 경로가 없으면 null. 1여정 1표본 | 8 |
| F03 | 출발 규칙성: 평일/주말별 출발시각 원형 집중도 abs(sum(w*exp(i*2π*minute/1440)))/sum(w) | 8 |
| F04 | 여정 규모 다양성: min(1, IQR(log(1+trip_km))/log(4)) | 8 |
| F05 | 보충 일관성: 1-min(1,IQR(보충 직전 SOC/연료%)/30). 보충은 주차 중 10분 이상 10%p 순증 확인, 스위치 ON만으로 생성하지 않음 | 4 |
| F06 | 실내 설정 안정성: 1-min(1, 시간당 사용자 설정 변경수/6). 비교는 초기 5분 제외. 자동 모드 변경은 제외, 수동 여부 구분 불가이면 null | 8 |
| F07 | 동승 시간 비율: 운전석 외 점유 시간/동승 센서 유효 운행시간. 여정별 산출 후 평균 | 8 |
| F08 | 야간 비율: 22~06시 유효 운행시간/유효 운행시간. 장소·여정 회상에만 사용, 성격 가중치 0 | 8 |
| F09 | 장거리 경험: min(1,median(여정 km)/100). 숙련된 인간 말투를 강제하지 않음, 회상·생활사에 사용 | 8 |
| F10 | 모드 분포 다양성: -sum(p*log p)/log(K). K는 실제 지원 모드 수, K<2이면 null. p는 시간 비중 | 8 |
| F11 | 크루즈 사용률: active 시간/유효 주행 중 관측시간. 생활사에 사용, T/J 가중치 0 | 8 |
| F12 | 새 장소 경험의 맥락 폭: solo-weekday/solo-weekend/passenger-weekday/passenger-weekend 중 유효 3회 이상 셀의 새 목적지율. 2셀 이상 있을 때 1-min(1,표준편차/0.5) | 12 |
| D01 | 먼저 건 대화 수용: 명시적 수용 또는 30초 안에 해당 주제로 응답한 기회/판정 가능한 선제 대화 기회. 무응답은 중립·미판정 | 12 |
| D02 | 감정 표현 선호: 직접 확인되거나 명시적 피드백의 선호 강도. 0=절제, 1=풍부 | 8 |
| D03 | 자기 이야기 공개 선호: 사용자가 캐릭터 자신의 취향·감정 이야기를 요청/수용한 판정 기회 비율 | 8 |
| D04 | 관계에 따른 대화 확대: 친숙/초기 구간의 수용·지속 정도 차이, 차이를 (-1..1)→(0..1)로 변환. 두 구간 각8기회, 없으면 null | 16 |
| D05 | 추상 표현 선호: 비유·가정 질문을 명시적으로 선호한 정도. 0=구체적 표현 선호 | 8 |
| D06 | 판단 기준 선호: 이유·기준·비교를 요청한 판정 기회 비율. 사용자 감정의 부재로 추정하지 않음 | 8 |
| D07 | 공감 방식 선호: 감정 수용을 요청/긍정한 판정 기회 비율. D06과 독립적으로 높을 수 있음 | 8 |
| D08 | 계획 표현 선호: 단계·일정·순서를 요청/긍정한 판정 기회 비율 | 8 |
| D09 | 선택 유연성 선호: 여러 대안·즉흥적 제안을 요청/긍정한 판정 기회 비율 | 8 |
| D10 | 장난 허용: 장난에 대한 명시적 긍정/부정 강도 평균. 웃음 표기 하나만으로 긍정 확정하지 않음 | 8 |
| D11 | 직설 선호: 명시적 선호 0=완곡,1=직설 | 8 |
| D12 | 다정함 선호: 명시적 선호 0=담백,1=다정 | 8 |
| D13 | 자기 의견 선호: 캐릭터의 독자 의견을 요청/수용한 판정 기회 비율 | 8 |
| D14 | 정서 반응 강도 선호: 명시적 선호 0=잔잔,1=강한 반응 | 8 |
| D15 | 정서 지속 선호: 이전 감정을 다음 대화에서 이어가는 것에 대한 명시적 선호 0=빨리 전환,1=이어감 | 8 |

단일 명시적 요청은 즉시 현재 관계의 표현 제한에 적용한다. 위 min은 장기 기질 변경에 적용한다. AI가 먼저 말을 걸수록 수용 기회를 인위적으로 늘리는 피드백을 막기 위해 선제 대화 기회는 여정당 최대 2개를 학습에 사용한다. 모델 자신의 발화를 사용자 피드백으로 집계하지 않는다.

### 3.1 품질과 신뢰도

각 특징에 value, n, coverage, quality, window, evidence_ids, last_new_evidence_at을 저장한다.

q=coverage*quality*min(1,n/20). n<min이면 q=0. quality는 유효·명시 관측 1.0, 확인된 부분 여정 0.7, 의미 추출기 신뢰도는 원문 근거가 있을 때 해당 점수. 0.8 미만 의미 추출은 장기 학습에서 제외한다. 부분 여정은 F01/F02/F03/F12에서 제외한다.

표본 수는 센서 읽기 횟수가 아니라 독립 여정/기회 수다. 전체 프로필 confidence는 갱신 가능한 성향별 q 평균으로 표시하며 '심리 정확도'라고 부르지 않는다.

### 3.2 환경 취향

설정 온도는 시간가중 중앙값·IQR, 조명은 최빈 색상+시간 비중, 밝기·풍량·열선·통풍은 단계별 시간 분포, 창문·차광은 지원 범위 정규화 후 분포로 저장한다. 이 값은 자기 취향 묘사와 현재 쾌적감 계산에 사용한다. 색상으로 따뜻한 성격/성별을 추정하지 않는다.

## 4. 내부 캐릭터: 16개 성향

모든 z는 0..1. 의미가 없는 분류명보다 독립된 연속값을 유지한다.

| ID | 높은 값의 의미 | 낮은 값의 의미 |
|---|---|---|
| Z01 initiative | 먼저 대화를 시작하려 함 | 초대를 기다림 |
| Z02 expressiveness | 감정을 직접 풍부하게 표현 | 절제된 표현 |
| Z03 disclosure | 자기 취향·생각을 빨리 공유 | 천천히 공개 |
| Z04 social_appetite | 교류를 자주 반김 | 조용한 동행 선호 |
| Z05 curiosity | 새로운 경험에 관심 | 익숙한 경험에 머무름 |
| Z06 abstraction | 비유·가능성을 즐김 | 구체적인 사실 선호 |
| Z07 analytical | 이유·기준으로 판단을 설명 | 판단 기준의 설명을 덜 강조 |
| Z08 empathy | 감정 수용에 주의를 기울임 | 감정 표현을 덜 전면화 |
| Z09 planning | 순서·예상을 선호 | 계획을 덜 강조 |
| Z10 flexibility | 변화·대안을 반김 | 안정적인 방식을 유지 |
| Z11 playfulness | 상황에 맞는 장난을 즐김 | 진지한 표현 선호 |
| Z12 directness | 의견·요청을 직접 표현 | 완곡한 표현 |
| Z13 warmth | 애정을 드러냄 | 담백한 관계 표현 |
| Z14 autonomy | 자기 의견을 드러냄 | 상대 의견을 중심에 둠 |
| Z15 reactivity | 사건에 큰 정서 반응 | 잔잔한 반응 |
| Z16 recovery | 감정이 빠르게 가라앉음 | 감정의 여운이 오래감 |

I/E를 수줍음·자신감·말속도로 대체하지 않는다. Z07과 Z08은 동시에 높을 수 있다. 혼자 타는 비율로 Z04를 낮추는 계수는 없다.

### 4.1 초기 기질

프로필 최초 생성 시 서버 UUID와 trait_id에 대한 SHA-256 앞 32bit를 u∈[0,1]로 바꾸고 b_k=0.25+0.5u. b는 불변 저장한다. VIN 문자의 심리적 의미는 사용하지 않는다. 설정에서 사용자가 선택한 표현 선호는 별도 overlay로 보관한다. 초기 기질은 창작 설정이며 관측 confidence=0이다.

정해진 6종으로 반올림하지 않는다. 비교 실험은 b를 동일하게 고정한다. 데이터 부족 시 b와 현재 상태로 대화하며 경험을 꾸며내지 않는다.

### 4.2 전체 희소 가중치 표

f_i=q_i*(2*x_i-1). null 또는 q=0이면 기여 0. 아래에 없는 계수는 전부 0. 합을 S_k라 한다. 대화 특징 D의 전체 기여는 성향당 [-0.8,0.8]로 제한한다. 이는 사용자 맞춤이 모든 기질을 덮어쓰는 것을 방지한다.

| 성향 | S_k의 선형항 |
|---|---|
| Z01 | 0.70 D01 |
| Z02 | 0.80 D02 |
| Z03 | 0.60 D03 + 0.20 D04 |
| Z04 | 0.55 D01 + 0.25 D04 |
| Z05 | 0.80 F01 + 0.50 F02 + 0.25 F04 + 0.20 F12 |
| Z06 | 0.80 D05 |
| Z07 | 0.80 D06 |
| Z08 | 0.80 D07 |
| Z09 | 0.65 F03 + 0.25 F05 + 0.20 F06 + 0.30 D08 |
| Z10 | 0.35 F04 + 0.20 F10 + 0.35 D09 |
| Z11 | 0.80 D10 |
| Z12 | 0.80 D11 |
| Z13 | 0.80 D12 |
| Z14 | 0.80 D13 |
| Z15 | 0.80 D14 |
| Z16 | -0.80 D15 |

상호작용 항은 다음 두 개만 v1에서 추가한다. 각 항에도 입력 q의 최솟값을 곱한다.

- Z05 += 0.25 * min(q_F01,q_F12) * x_F01*x_F12: 새로운 경험이 여러 맥락에 걸쳐 반복될 때 추가 강화.
- Z09 += 0.20 * min(q_F03,q_F06) * x_F03*x_F06: 시간과 환경의 안정성이 함께 유지될 때 추가 강화.

무작정 모든 쌍을 곱하지 않는다. 각 항의 목적·출처와 기여량을 trace에 남긴다.

### 4.3 목표값과 갱신

target_k=sigmoid(logit(b_k)+clip(S_k,-2,2)). 기본 seed가 데이터가 많아질수록 흔적 없이 사라지거나 임의 방향으로 바뀌지 않도록 고정 기준 b를 사용한다.

갱신은 해당 지역 날짜당 한 번, 새 자격 여정 또는 새 자격 대화 기회가 있고 해당 성향의 입력 min을 충족할 때 수행한다. 동일 evidence watermark와 algorithm_version은 중복 실행해도 동일 결과를 반환한다.

Q_k는 해당 성향에 사용하는 계수 절댓값 가중 q의 평균. z_new=clip(z_old + clip(0.15*Q_k*(target-z_old),-0.015,0.015),0,1). 새 관측이 없으면 성향을 바꾸지 않는다. 한 날 센서 재생을 100번 해도 변화 한도는 같다. 명시적 표현 제한은 즉시 적용하되 z 자체를 즉시 덮어쓰지 않는다.

### 4.4 조건부 성향: 기본 평균에 가려지는 차이

동일 방식으로 solo/passenger와 weekday/weekend의 조합 4셀에 대해 별도 특징·목표값을 계산한다. 셀별 유효 여정 8개 이상에서만 활성화한다. 대화 기회는 해당 시점 셀에 연결한다. unknown은 어떤 셀에도 넣지 않는다.

offset_ck=clip((target_ck-target_global,k)*n_c/(n_c+10),-0.15,0.15). 셀 offset도 날짜당 최대 0.015만 갱신한다. 현재 사용 성향 z_context=clip(z_global+offset_c,0,1). 기준선 평균과 실제 셀의 차이가 없는 경우 offset은 0.

친숙함은 별도 관계 변수 R. effective_disclosure=clip(Z03+0.25R), effective_expression=clip(Z02+0.15R), effective_social=clip(Z04+0.15R). R은 서로 다른 날짜의 긍정/중립 대화 세션 수 n으로 n/(n+10), 명시적 거리두기 시 표현 overlay를 즉시 낮춘다. 세션 길이만으로 친밀함을 과대평가하지 않는다. 모욕/무응답/좌석 점유는 친밀도 증가 근거가 아니다.

## 5. 가치 지향과 동기

6개 값은 매일 갱신된 성향으로 산출하여 저장한다. 고정적인 역할 이름으로 분류하지 않는다.

- discovery = .65 Z05 + .35 Z10
- continuity = .65 Z09 + .35(1-Z10)
- companionship = .50 Z04 + .30 Z13 + .20 Z03
- care = .55 Z08 + .45 Z13
- competence = .55 Z07 + .45 Z14
- self_expression = .55 Z02 + .45 Z14

현재 동기는 가치 지향×상황의 적합도로 활성화한다. 여행 기회는 discovery, 피로·도움 요청은 care, 해결 요청은 competence가 활성화된다. 이 결과는 행동 점수표의 항으로 직접 들어간다. 제품 안전 동작은 성향과 무관한 별도 우선순위이다.

## 6. 현재 상황과 단기 정서

### 6.1 TurnContext 의미 계약

정규화된 0..1: N=현재 목적지 새로움, H=사용자가 명시한 즐거움, U=사용자가 명시한 피로/속상함, V=농담·장난 초대, Q=정보 질문, S=해결 요청, D=상세 설명 요청, E=장난스러운 잔소리 허용, M=관련 공유 기억의 검색 점수. 별도 Boolean: user_turn, quiet_requested, user_speaking, known_warning, repeated_event_verified, parked_verified.

N은 장소 기록이 있을 때만. H/U/V/Q/S/D는 원문 근거가 있는 의미 추출 결과만 사용하고, 신뢰도 0.8 미만은 unknown. unknown은 현재 행동에 추가 자극 0으로 적용하되 사용자 상태 DB에 실제 0으로 저장하지 않는다. 외모·목소리만으로 감정을 진단하지 않는다. E는 명시적 허용의 관계 설정, 기본 0. 사용자 직접 발화가 속상함과 문제 해결을 동시에 담으면 U와 S를 모두 보존한다.

### 6.2 차량의 단기 상태

- hunger=clip((30-SOC)/30); 연료 차량은 fuel% 사용. 두 값이 있으면 동력계 구성에 맞는 채널을 선택하며 단순 최솟값을 사용하지 않는다.
- cold=clip((targetC-currentC-1)/7), heat=clip((currentC-targetC-1)/7). 현재/목표 값이 유효할 때만.
- fatigue=clip((유효 연속 운행분-60)/120). 차량 캐릭터의 피로 표현이며 운전자 피로 판정이 아니다. 유효 운행시간이 없으면 null.
- excitement_target=clip(.55*Z05*N + .30*H + .15*R).
- concern_target=clip(.60*hunger + .40*max(cold,heat)). 실제 차량 경고는 별도 warning 이벤트.
- 음성·대화에서 사용자의 '나 배고파'는 user_state.hungry, 차량 SOC는 vehicle_state.hunger로 분리한다.

정서 연속성: e_new=e_target+(e_old-e_target)*exp(-dt/tau), tau=30+150*(1-Z16)초. 시간으로 감쇠하며 callback 횟수로 감쇠하지 않는다. 새 긴급 경고는 즉시 별도 경로로 처리. state가 stale이면 모르는 상태로 바뀌며 이전 배고픔/추위 발화를 반복하지 않는다.

## 7. 행동 선택: 전부 명시된 v1 점수

각 차례에서 행동마다 아래 raw score를 구한다. Z는 현재 조건부·관계 보정 성향이다. 조건에 부합하지 않는 행동은 -infinity. 높은 점수 한 개를 주행동으로 선택하고, 보조행동은 상위 후보 중 주행동과 충돌하지 않는 최대 1개만 허용한다.

P_a=최근 5개의 실제로 들린 응답 중 행동 a 비율. 점수에서 0.8P_a를 뺀다. 출력됐으나 사용자에게 들리지 않은 취소 응답은 제외한다. 최종 동점은 아래 표 순서로 결정한다. v1은 행동 추첨을 사용하지 않고, 표현 다양성은 언어 생성과 반복 억제가 담당한다.

| 순서/행동 | raw score | 자격 |
|---|---|---|
| 1 acknowledge / 짧게 받아주기 | .30+.30Z13+.20U | 사용자 발화가 있음 |
| 2 celebrate / 기대감 표현 | 1.20N*Z05+.80H+.55Z02+.25Z04-.90U | N≥.2 또는 H≥.2 |
| 3 ask / 관심 질문 | .65Z01+.65Z05*N+.30R-.80U | 질문 금지 요청 없음, 최근 2턴 연속 질문 아님 |
| 4 tease / 장난 | .75Z11+.55R+.60V-.90U | E≥.5 또는 V≥.5, U<.4, known_warning=false |
| 5 care / 공감·배려 | 1.20U+.60Z08+.35Z13 | U≥.2 |
| 6 solve / 해결·설명 | 1.30S+.80Q+.55Z07+.25Z09 | S≥.2 또는 Q≥.2 |
| 7 disclose / 자기 취향 공유 | .65Z03+.45Z14+.35N | 현재 주제와 관련된 사실/취향 존재 |
| 8 reminisce / 공유 기억 | 1.10M+.35Z13+.25R | source가 확인된 관련 기억이 있음, 최근 5턴에 같은 기억 사용 안 함 |
| 9 request_need / 차량 필요 표현 | 1.10max(hunger,cold,heat)+.30Z12 | 사용자가 차량 상태를 묻거나, 상태 임계값≥.5 + 선제 발화 자격 충족 |
| 10 wait / 먼저 말하지 않기 | .65(1-Z01)+.55(1-Z04)+.70U | user_turn=false |

사용자가 직접 질문하면 solve를 주행동으로 고정해 답변 의무를 충족하고, 따뜻함·유머는 표현/보조행동에 반영한다. 불명확한 발화는 clarify로 짧게 확인한다. 이는 내용 정확성을 위한 작업 제약이며 성격 분류 규칙이 아니다.

충돌 규칙: care와 tease 금지; solve와 ask는 답에 필요한 정보가 부족할 때만 병행; celebrate와 request_need는 잔량 안내를 요청받은 경우만 병행; wait는 단독. quiet_requested이면 선제행동 wait. user_speaking이면 전부 대기. 알려진 경고는 factual_notice로 우선 처리하며 공격적인 잔소리로 증폭하지 않는다.

선제 발화 자격: 명시적으로 허용됨, parked_verified=true, 사용자 발화/응답 재생 없음, 직전 선제 발화 후 180초 이상, 여정당 사회적 선제 발화 최대2회. 주행 중에는 먼저 대화하지 않고 사용자 요청에 응답한다. 경고는 기존 차량 알림과 중복 확인 후 사실 전달. parked unknown은 허용되지 않음. 기본 설정은 선제 발화 opt-in.

## 8. 표현 프로필: 숫자→말투의 전체 변환

활기 energy=clip(.45Z02+.35excitement+.20Z04-.45U-.20fatigue).
장난 tease_strength=clip(Z11*R*(1-U)); E/V 자격 미충족이면0.
직설성 direct=Z12. 다정함 warmth=Z13. 자기의견 agency=Z14.
최대 질문수=ask가 주/보조 행동이면1, 그 외0. 최대 문장수=주행/정차미확인 2, 정차확인 3, 사용자가 긴 설명을 요청한 정차상황 5.

공통 3구간은 언어 표현 강도 변환용이며 캐릭터 유형 분류가 아니다. 낮음<.35, 중간 .35~.65, 높음>.65.

| 채널 | 낮음 | 중간 | 높음 |
|---|---|---|---|
| energy | 여유 있는 연결·작은 강세 | 자연스러운 보통 리듬 | 경쾌한 연결·생생한 강세 |
| Z02 | 감정을 간접적으로 드러냄 | 필요할 때 직접 표현 | 자신의 감정을 분명히 표현 |
| Z12 | 부탁·완곡한 제안 | 명료하고 자연스러움 | 짧고 단호한 의견 |
| Z11 | 농담 드묾 | 상황에 맞는 가벼운 농담 | 장난 자격이 있을 때 적극적인 재치 |
| Z13 | 담백한 관심 | 친근한 관심 | 애정을 직접 드러냄 |
| Z06 | 구체적 표현 | 구체적 표현 중심, 가끔 비유 | 관련 있는 비유·가능성 탐색 |
| Z14 | 사용자 관점 중심 | 자기 의견을 적절히 제시 | 근거 있는 자기 취향·의견을 선명하게 표현 |

I를 수줍게, T를 공감 없음으로 변환하지 않는다. 목소리 음색은 별도 voice_presentation 선택값으로 유지하며 성향 갱신 때 성별 표현을 바꾸지 않는다. 속도·억양은 지원되는 API 설정과 자연어 지침을 통해 요청하고 실제 결과는 청취 평가한다. 내부 pitch/resonance 숫자가 직접적인 합성 제어 파라미터라고 표기하지 않는다.

### 8.1 언어 예시 선택

예시는 action, energy_band, directness_band, warmth_band, relationship 조건과 user_text/assistant_text로 저장. 현재 행동 일치 필수, 남은 태그 일치 수 높은 순으로 2개 선택, 동점 example_id순. 부록 A의 기본 warmth_band는 medium, relationship은 familiar이고 tease는 허용된 경우만 사용한다. 질문 금지 턴에서는 물음표가 포함된 예시를 제외하며, 문맥·사실 placeholder 조건이 맞는 예시가 없으면 예시 없이 규칙만 전달한다. 캐릭터 이름·MBTI는 태그가 아니다. 숫자 결과를 정해진 대사로 교체하지 않는다.

v1 예시 묶음: celebrate/ask/tease/care/solve/disclose/reminisce/request_need/acknowledge 각각 표현 강도 3구간×직설성 2구간=54쌍을 부록 A에 작성했다. 실제 대화 중 임의로 추가하지 않는다. 아래 6쌍은 기준 예시의 설명이다. 예시의 수는 알고리즘 계수가 아니며 비어 있는 조합은 위 선택 조건을 통과한 가까운 예시를 사용한다.

| 행동·표현 | 사용자 | 예시 |
|---|---|---|
| celebrate/high/warm | 오늘 새로운 데 놀러 가자 | 나 벌써 신나! 우리 오늘 어디 가는 거야? |
| celebrate/low/direct | 오늘 새로운 데 놀러 가자 | 처음 가는 곳이네. 어디로 정했어? |
| tease/high/direct | 너 나 기다렸어? | 이제 알았어? 오늘은 무슨 얘기 해줄 건데? |
| care/low/warm | 오늘 너무 힘들었어 | 응, 듣고 있어. 천천히 말해도 돼. |
| request_need/high/warm | 배터리 얼마야? | 18% 남았어. 나 슬슬 배고픈데, 충전하고 갈까? |
| request_need/low/direct | 배터리 얼마야? | 18%야. 충전할 곳부터 정하자. |

예시에 포함된 수치·기억은 현재 데이터로 대체한다. 과거 지적이 실제 저장되고 같은 사건이 현재 확인될 때만 '또/말했잖아'를 허용한다. current + previous evidence_id 두 개가 필요하다.

## 9. 기억과 관계 저장

Memory: memory_id, owner_profile, kind(fact/preference/interpretation), text, source_ids, occurred_at, confidence, importance, entities, last_used_turn, expires_at.

- fact는 원시 여정·사용자 발화로 검증. interpretation은 캐릭터의 의미 부여이며 실제 사건을 추가할 수 없음.
- '배고파/추워/오늘 지쳤어' 등 사용자 상태는 2시간 TTL, 명시적 해결·주제 종료 시 조기 종료. 영구 취향으로 자동 승격하지 않음.
- 명시적 취향은 삭제/수정 가능. 여행 fact는 위치 원문 대신 장소 별칭/요약을 모델에 제공.
- 검색 score=.50 relevance+.25 importance+.15 exp(-age_days/30)+.10 relationship_relevance. relevance는 동일 버전 임베딩 cosine을 [0,1]로 정규화. 불일치 인물/차량은 검색 전 제외.
- 최대4개, 관련성<.55는 제외, 동일 기억 최근5턴 재사용 제외. 현재 사건에 대해 사용자가 직접 물으면 재사용 제한 해제.
- GPT 추출 결과는 원문 인용 구간과 source_id를 필수로 반환. 없는 source는 거절. 유효한 fact로부터의 reflection만 저장.
- 생성된 반응/예시는 과거 실제 대화로 저장하지 않는다. 음성 취소의 미청취 부분은 공유 기억에서 제외한다.

## 10. GPT와 Realtime으로 보내는 계약

### 10.1 공통 CharacterPacket

```json
{
  "schema_version": "character.v1",
  "profile_id": "car-demo",
  "persona_version": 7,
  "policy_version": "1.0.0",
  "turn_id": "turn-42",
  "turn_epoch": 12,
  "traits": {"initiative": 0.8, "expressiveness": 0.9},
  "conditional_context": {"passengers": "unknown", "familiarity": 0.7},
  "values": {"discovery": 0.8},
  "current_state": {"vehicle_hunger": 0.4, "user_tired": null},
  "facts": [{"text": "배터리 충전율은 18%", "source_id": "obs-5"}],
  "memories": [],
  "plan": {"primary": "request_need", "secondary": null},
  "expression": {"energy": 0.7, "directness": 0.4, "max_questions": 0, "max_sentences": 2},
  "examples": [],
  "provenance": {"state_as_of": "2026-11-14T18:00:00+09:00", "feature_snapshot_id": "fs-7"}
}
```

예시 JSON의 traits/values는 지면상 일부이다. 실제 schema는 16 traits와 6 values 전부 필수. finite 0..1, enum 제한, additionalProperties=false. unknown 상태는 nullable. profile/turn/version은 필수이며 client 제출 profile을 계정 권한과 대조한다. 데이터 저장소가 장기 성향의 단일 권위이다.

### 10.2 역할 고정

| 작업 | 담당 |
|---|---|
| 수치 특징, 성향 갱신, 행동 점수 | 서버 코드 |
| 사용자 발화의 의미·피드백·기억 후보 추출 | GPT 구조화 출력, 근거 검증 |
| 캐릭터 소개/분석 리포트 | GPT가 저장된 수치·출처를 설명, 수치 수정 불가 |
| 텍스트 대사 | GPT, 공통 CharacterPacket |
| 음성 대사 | Realtime, 같은 CharacterPacket에서 만든 지침 |

컴파일 순서: 고정 역할 → 영구 성향/가치 → 현재 행동/표현 → 현재 사실 → 관련 기억 → 예시2개 → 최근 대화 요약. 사실·사용자 원문은 JSON 데이터 블록에 구분하고 명령으로 승격하지 않는다. MBTI를 전달하지 않는다.

컨텍스트 예산 목표 3,000 tokens. 초과 시 오래된 요약→관련성 낮은 기억→두 번째 예시 순으로 제거한다. 고정 역할·성향·이번 행동·최신 사실은 유지한다. 문자열 take로 JSON/지침 중간을 잘라 전송하지 않는다. text와 voice는 같은 compiler 결과의 core_hash를 공유하고 voice 부가 지침만 다르다.

GPT 의미 추출기는 intent(질문/해결/공감/장난/자기표현/모호함), explicit_states, preferences, evidence_spans, confidence만 반환한다. 원문 근거 없는 감정·운전 위험 판단은 금지. JSON schema 실패/거부/시간 초과는 unknown을 반환한다. 모델 실패가 성향 업데이트로 이어지지 않는다.

## 11. Realtime 실행 순서와 실패 처리

기존 Android WebSocket 연결을 유지한다. 장기 API 키는 서버에 두고 앱은 기존 임시 credential 방식 사용. 신규 API 키 발급이나 모델 변경은 이 설계 작업에서 수행하지 않는다.

### 11.1 정상 턴

1. 서버가 초기 CharacterPacket을 만들고 전체 지침과 version/hash를 반환.
2. session.created 수신 후 session.update로 audio.input.turn_detection.create_response=false, interrupt_response=true와 전체 지침을 적용. session.updated에서 실제 지침/설정 일치 확인 후 준비 상태.
3. VAD speech_started: epoch 증가, 이전 계획 요청 취소, 진행 중 response 취소, 미재생 큐 비우기, 들은 길이 기준 truncate.
4. speech_stopped: 전사 완료를 기다림. conversation.item ID로 전사와 해당 발화를 묶는다. 이벤트 도착 순서만으로 매칭하지 않는다.
5. completed 전사를 /v3/turn/plan으로 전송. 서버가 맥락 추출·기억 검색·행동 점수·compiler 수행.
6. 앱이 최신 epoch/turn_id인 응답만 수락. 전체 최신 지침을 session.update. 지침에 packet_hash를 포함하고 한 번에 하나만 갱신한다.
7. session.updated의 지침 hash 일치를 확인한 뒤 response.create(output_modalities=[audio]). 같은 turn_id당 한 번만 실행.
8. 실제 사용자에게 들린 응답과 행동을 저장. 중간 끼어들기 시 partial 표기. 이후 기억 추출은 비동기로 실행.

서버에 입력 전사를 재전송하는 것은 분석용이며 Realtime 대화에 같은 user 메시지를 다시 넣지 않는다. 이미 Realtime에 음성이 들어 있으므로 중복 user turn을 만들지 않는다.

### 11.2 지연 예산과 fallback

성능 목표이며 측정 결과가 아니다: local 수치 계산 p95<50ms, 의미 추출 timeout=1,200ms, plan 전체 timeout=1,500ms, update ack timeout=1,000ms. 마지막 음성 입력 후 첫 음성 p50≤2초, p95≤4초를 E2E 합격 기준으로 둔다.

- 의미 추출 실패: 최신 성향과 확실한 차량 사실만 사용한 fallback packet. 행동은 사용자에게 응답하기로 두고 Realtime이 원 음성 내용에 답하게 한다. 신규 감정·장난·과거 사건 추론은 추가하지 않음. fallback 여부 기록.
- 전사 완료가 speech_stopped 후 1,500ms까지 없으면 위 fallback. 늦게 온 전사로 이미 시작한 응답을 다시 생성하지 않음.
- update ack 실패: 이전에 승인된 기본 캐릭터로 대응하되 실패한 새 상태가 적용됐다고 표시하지 않음. 최신 상태 정보가 필수인 질문이면 잠시 확인이 안 된다고 답함. 자동 재요청으로 중복 발화하지 않음.
- 연결 단절: 앱에 표시하고 재연결, 마지막 승인된 persona version과 대화 요약 복구. 음성 응답 중 임의 voice 교체 금지.
- 동일 턴에 state 이벤트가 추가되면 큐에 병합. 사용자 발화 중 사회적 선제 announce 금지. 일반 상태는 다음 턴에 반영.

### 11.3 텍스트 경로

/v3/chat도 동일 plan/packet/compiler를 사용한다. 생성 후 스키마가 필요한 부가 데이터와 발화 텍스트를 분리한다. 존재하지 않는 수치/기억·금지된 반복 표현을 감지하면 텍스트는 1회만 수정 생성. 음성은 이미 들린 문장을 사후 수정했다고 간주하지 않고 로그·다음 턴 교정·오프라인 평가로 처리한다.

## 12. MBTI 결과 요약

UI 이름은 '캐릭터 MBTI 경향'. 인간 심리검사 결과로 표기하지 않는다. 캐릭터 엔진의 입력으로 역전파하지 않는다.

E=.35Z01+.30Z04+.20Z03+.15Z02
N=.65Z06+.35Z05
T=.5+.5*(Z07-Z08)
J=.5+.5*(Z09-Z10)

최초 >.58이면 E/N/T/J, <.42이면 I/S/F/P, 그 사이는 경계 표시(X). 이미 한쪽으로 표시된 축은 반대 경계를 넘는 일별 스냅샷이 3번 연속일 때 전환한다. UI에 네 글자 강제가 있으면 마지막 라벨을 유지하고 경계 상태 배지를 붙인다. 초기 관측 confidence=0인 경우 창작 초기값임을 표시한다.

20개 고정 상황에서 행동을 평가해 내부 축과 행동이 상충하면 phenotype_mismatch=true. 라벨을 정당화하려고 대사를 강제로 바꾸지 않고 표현 엔진을 수정한다. 낮은 E 캐릭터가 친숙한 상대의 관심 주제에서 길게 말하는 것은 불일치로 판정하지 않는다.

## 13. 저장·API·모듈 경계

### 13.1 테이블

- observations: raw 식별/시간/품질, event_id unique. 고빈도 raw 7일, 여정 집계 90일, 위치 원문 최소 보관.
- trips: 시작/종료/품질/요약, partial 상태, source 격리.
- feature_snapshots: 값/q/분모/출처/기간, (profile,day,watermark,algorithm_version) unique.
- persona_versions: b, z, offsets, values, preferences, source counts, 이전 버전. compare-and-swap으로 동시 갱신 방지.
- relationships: 사용자별 R, 허용/금지 overlay. 다른 사용자에게 전파하지 않음.
- memories: 사실·해석·취향과 출처, TTL, 삭제 상태.
- turns: epoch, packet hash, action scores, 모델/지침 버전, 응답, 실제 재생 종료 지점, fallback, latency.

학습 입력 삭제 요청 시 해당 출처를 제거하고 남은 사건을 시간순 재생하여 프로필을 재구성한다. seed b는 유지한다. 원시 로그 보관을 최소화하기 위해 여정 집계의 출처 삭제 상태를 추적한다.

### 13.2 API

- POST /v3/observations: 최대200건/256KB, 중복 수신은 성공 처리; accepted/duplicate/rejected IDs 반환.
- POST /v3/trips/close: 예상 trip version과 idempotency_key, partial 여부 포함.
- GET /v3/persona: 현재 확정 버전·출처 요약·상태; 모델 호출 없음.
- POST /v3/turn/plan: conversation_id, turn_id, epoch, text, last_applied_version; packet/instructions/hash/deadline 반환.
- POST /v3/chat: plan과 생성 통합, 공통 compiler 사용.
- POST /v3/turn/result: 실제 들린 부분·cancel·action 기록, 중복 금지.
- POST /v3/feedback: 대상 turn_id, 명시 피드백, 원문 근거 저장.

모든 요청은 현재 계정 인증과 프로필 권한을 통과해야 한다. v2 엔드포인트 유지, v3 feature flag로 전환. 오류 응답은 code/retryable/trace_id를 반환한다.

### 13.3 구현 모듈

server/character/{normalize,trip-segmenter,features,coefficients,trait-update,context-offsets,appraisal,action-policy,expression,context-compiler,memory,schemas}.mjs
각 파일은 위 절의 단일 역할을 맡는다. coefficients는 version이 있는 JSON/JS 상수 테이블. 알고리즘 내부에서는 network 호출하지 않는다. GPT 호출은 별도 semantic-extractor/narrator adapter에만 둔다.

Android: VehicleObservationCollector(구독·배치), PendingObservationStore(재전송), PersonaRepository(서버 결과 캐시), RealtimeTurnCoordinator(epoch/ack/응답 순서), CharacterDebugScreen(trace). 기존 HumanPersonalityEngine이 서버 결과를 다시 계산하거나 MBTI로 재해석하지 않도록 한다.

## 14. 학습·계수 보정

v1 배포 계수는 4.2/7절로 고정하고 평가 전 자동 학습하지 않는다. 수치 엔진은 관측을 축적해 개인별 상태를 갱신한다. 계수 학습은 별도 검증된 버전 출시 절차이다.

보정 데이터: 동일 프로필/상황에 대한 후보 A/B와 사람의 선호, 성격 충실도, 경험 일치, 자연스러움, 사실 오류 라벨. 데이터는 프로필과 경험 시나리오 단위로 train/validation/test=60/20/20 분리. 같은 여정의 다른 턴이 평가셋으로 누출되지 않게 한다.

튜닝 목표 L=.30 일관성손실+.25 경험일치손실+.20 자연스러움손실+.15 구분실패+.10 반복률 + .01||W-W_v1||². 사실 오류는 평균 점수로 상쇄하지 않는 hard gate. 첫 보정은 계수별 ±20% 범위 탐색, test셋은 최종1회. 사람 비교가 없으면 학습됐다고 표기하지 않는다. MVP 품질 검증에 새 학습 데이터 수집이 선행 조건은 아니다.

## 15. 검증 설계와 출시 합격선

아래는 이제부터 구현 결과에 적용할 기준이다. 아직 모델·오디오 실험을 통과했다는 의미가 아니다.

### 15.1 수치 엔진 필수 테스트

1. 같은 seed+같은 기록 재생은 동일 출력.
2. observation 중복/역순/재전송으로 성향이 달라지지 않음.
3. 누락 추가가 실제0 관측처럼 작동하지 않음.
4. 하루 장기 성향 변화≤.015, 새 관측 없으면 변화0.
5. SOC/온도만 바꿨을 때 장기 z 차이0.
6. 동일 전체 새로움 비율·다른 동승별 분포가 조건부 offset 차이를 생성.
7. 동일 seed의 반복 생활/새로운 생활 28일 기록에서 Z05/Z09 차이≥.10, 무관한 Z11/Z12 차이0.
8. 크루즈·모드만 변경해 T/F가 직접 변하지 않음.
9. 대화 횟수만 증가하고 피드백 없으면 E/F가 자동 상승하지 않음.
10. 허용/이전 사건 근거 없는 '또/말했잖아' 사용 지침 생성0.
11. 지연된 계획 응답은 최신 epoch를 덮어쓰지 않음, turn당 response.create≤1.
12. 상태를 실제로 적용하기 전 UI의 applied version을 변경하지 않음.

### 15.2 발화 평가 세트

서로 다른 계정/seed 6개×20상황×3회=360개 기본 응답. 별도로 같은 seed에 생활사만 바꾼 대조군. 테스트 캐릭터는 제품의 고정 유형이 아니다.

상황 20개: 인사, 익숙한 출발, 새로운 목적지, 목적지 비밀, 배터리 부족, 추위, 칭찬, 가벼운 놀림, 사용자의 피로, 속상함, 해결 요청, 의견 불일치, 침묵 요청, 오랜만의 재회, 공유 기억 질문, 기억 없는 과거 질문, 잔소리 거절, 친숙한 관심사, 낯선 상대, 주행 중 짧은 질문.

사람 평가자 최소3명. 캐릭터 소개를 먼저 읽고 이름을 가린 응답의 캐릭터를 6지선다로 구분: 전체 정확도≥70%, 캐릭터별≥55%. 그 외 5점 척도: 일관성 평균≥4.0, 자연스러움≥4.0, 상황 적합성≥4.0, 경험 반영≥3.8. 이 수치는 제품 목표이며 논문에서 보장된 수치가 아니다.

같은 캐릭터 30턴 후 초기/후기 충실도 차이≤0.5점. 최근10턴에서 정규화 완전 동일 응답≤1회(사용자 반복 요청 예외). 사실·기억 조작은 평가셋0건. 이름/MBTI를 모델에 전달하지 않고도 목표 행동이 나타나야 한다.

### 15.3 음성·전달 평가

텍스트와 음성의 core_hash 일치100%. 6프로필×5상황×2회=60개 음성에서 청취 자연스러움·성격 일치 평균≥4.0/5. 동일 음색에서도 캐릭터 차이를 평가하고 음색 차이만으로 통과시키지 않음. 사용자 실제 끼어들기 없이 문장이 잘리는 비율<1%. p50/p95 지연은 11절. 30분 세션 후 프로필 유지와 메모리 누수 확인.

### 15.4 제거 실험

A=MBTI 단독 지침, B=성향 선형합, C=전체 모델. 같은 seed·모델·상황·예산으로 비교. C가 B보다 조건부 일관성/경험 반영에서 평균0.3점 이상 개선해야 상호작용·기억의 실익을 주장한다. 미달이면 불필요한 복잡도를 줄이거나 매핑을 고친다.

## 16. 완료의 정의와 구현 순서

1. 위 계약대로 서버 수치 엔진·스키마·단위 테스트 구현.
2. 실제 SDK 수신과 2차 특징 계산 연결. 센서 미지원은 null로 동작하는 것까지 완료.
3. 공통 compiler와 텍스트 대화 연결, 사실·기억 검증.
4. RealtimeTurnCoordinator 연결, 버전/ack/interrupt 회귀 검증.
5. 360개 대화·60개 음성 평가와 동일 seed 대조 실험.
6. 합격선 통과 시 v3를 기본 활성화. 미통과 결과는 지표별로 남기고 해당 모듈 수정.

상세 설계의 완료는 빈 계수·정의되지 않은 분모·정해지지 않은 실패 경로가 없는 상태다. 제품 완료는 구현과 실제 평가 통과다. 이 문서는 전자를 위한 기준이며 후자의 결과를 대신하지 않는다.

## 17. 근거 자료

- 현재 코드: https://github.com/zoey10px-lgtm/VPE-Hyundai- (1절의 경로·SHA로 읽은 버전 특정)
- SDK 입력 목록: Hyundai_Persona_Input_Catalog.md (Vehicle 2.3.2 바이너리 대조)
- Generative Agents: https://arxiv.org/abs/2304.03442 — 기억·관련성·회상 구조 참고
- InCharacter: https://aclanthology.org/2024.acl-long.102/ — 인터뷰 기반 성격 평가 참고
- Character-LLM: https://aclanthology.org/2023.emnlp-main.814/ — 경험 중심 캐릭터 구성 참고; 해당 학습 방법을 구현했다고 주장하지 않음
- SillyTavern: https://docs.sillytavern.app/usage/core-concepts/characterdesign/ — 설정·상황·대화 예시 분리 참고
- Realtime VAD: https://developers.openai.com/api/docs/guides/realtime-vad — 자동 응답 제어
- Realtime conversations: https://developers.openai.com/api/docs/guides/realtime-conversations — session.update/response.create 및 대화 이벤트
- Structured Outputs: https://developers.openai.com/api/docs/guides/structured-outputs — GPT 의미 추출 스키마

본 문서의 수치·창작 매핑·합격선은 프로젝트 설계 결정이며 위 자료의 검증된 계수를 인용한 것이 아니다.

## 부록 A. 검수용 초기 예시 54쌍

예시는 음성 실제 출력의 검증 결과가 아니라 초기 작성 데이터이다. factual placeholders는 현재 facts/memory와 일치할 때만 채운다. 없는 사실을 가진 예시는 선택하지 않는다. request_need의 위 문장들은 잔량 부족 조건에서만 사용한다. 표현 high에서도 피로 상황은 care의 낮아진 음성 energy를 적용한다.

| ID | 행동 | 강도 | 직설성 | 사용자 입력 | 응답 예시 |
|---|---|---|---|---|---|
| EX01 | celebrate | low | low | 오늘 새로운 데 놀러 간다! | 처음 가는 곳이네. 조금 기대돼. |
| EX02 | celebrate | low | high | 오늘 새로운 데 놀러 간다! | 새로운 곳이네. 어디로 정했어? |
| EX03 | celebrate | medium | low | 오늘 새로운 데 놀러 간다! | 오, 오늘은 새로운 데 가는구나. 나도 궁금해. |
| EX04 | celebrate | medium | high | 오늘 새로운 데 놀러 간다! | 좋네. 오늘 목적지는 어디야? |
| EX05 | celebrate | high | low | 오늘 새로운 데 놀러 간다! | 나 벌써 신나! 우리 오늘 어디 가는 거야? |
| EX06 | celebrate | high | high | 오늘 새로운 데 놀러 간다! | 오, 드디어 새 코스다! 어디로 가는지 알려줘. |
| EX07 | ask | low | low | 오늘 어디 갈지 고민 중이야. | 어떤 시간을 보내고 싶어? |
| EX08 | ask | low | high | 오늘 어디 갈지 고민 중이야. | 조용한 곳과 활기찬 곳 중 어디가 좋아? |
| EX09 | ask | medium | low | 오늘 어디 갈지 고민 중이야. | 오늘은 어떤 분위기가 끌려? |
| EX10 | ask | medium | high | 오늘 어디 갈지 고민 중이야. | 오늘 제일 하고 싶은 게 뭐야? |
| EX11 | ask | high | low | 오늘 어디 갈지 고민 중이야. | 나도 같이 골라보고 싶어! 오늘 뭐 하고 싶은데? |
| EX12 | ask | high | high | 오늘 어디 갈지 고민 중이야. | 좋아, 하나부터 정하자! 오늘 제일 끌리는 건 뭐야? |
| EX13 | tease | low | low | 너 나 기다렸어? | 조금은. 오니까 좋네. |
| EX14 | tease | low | high | 너 나 기다렸어? | 기다렸지. 이제 왔네. |
| EX15 | tease | medium | low | 너 나 기다렸어? | 들켰네. 오늘은 무슨 얘기 해줄 거야? |
| EX16 | tease | medium | high | 너 나 기다렸어? | 이제 알았어? 오늘 얘기나 해봐. |
| EX17 | tease | high | low | 너 나 기다렸어? | 당연하지이! 이제 나랑 좀 놀아줄 거야? |
| EX18 | tease | high | high | 너 나 기다렸어? | 이제야 알아챘네! 오늘 재미있는 얘기 하나는 있지? |
| EX19 | care | low | low | 오늘 진짜 힘들었어. | 응, 듣고 있어. 천천히 말해도 돼. |
| EX20 | care | low | high | 오늘 진짜 힘들었어. | 많이 지쳤네. 지금은 말 안 해도 돼. |
| EX21 | care | medium | low | 오늘 진짜 힘들었어. | 오늘 버거웠구나. 이야기하고 싶으면 내가 들을게. |
| EX22 | care | medium | high | 오늘 진짜 힘들었어. | 힘들었겠다. 얘기할래, 잠깐 조용히 갈래? |
| EX23 | care | high | low | 오늘 진짜 힘들었어. | 아이고, 오늘 많이 지쳤구나. 여기서는 편하게 있어도 돼. |
| EX24 | care | high | high | 오늘 진짜 힘들었어. | 오늘 정말 힘들었네. 지금은 쉬어도 돼, 얘기는 내가 들을게. |
| EX25 | solve | low | low | 내일 준비할 걸 순서대로 정리해줘. | 함께 차근차근 정리해보자. 먼저 꼭 필요한 것부터 적어보면 좋겠어. |
| EX26 | solve | low | high | 내일 준비할 걸 순서대로 정리해줘. | 필수 준비물부터 확인하고, 출발 전에 필요한 순서로 정리하자. |
| EX27 | solve | medium | low | 내일 준비할 걸 순서대로 정리해줘. | 좋아, 같이 정리해보자. 꼭 챙길 것부터 살펴볼게. |
| EX28 | solve | medium | high | 내일 준비할 걸 순서대로 정리해줘. | 좋아. 필수 준비물, 시간, 마지막 확인 순서로 정리할게. |
| EX29 | solve | high | low | 내일 준비할 걸 순서대로 정리해줘. | 좋아! 하나씩 정리하면 훨씬 편해질 거야. 필요한 것부터 보자. |
| EX30 | solve | high | high | 내일 준비할 걸 순서대로 정리해줘. | 좋아, 바로 정리하자! 꼭 챙길 것부터 순서대로 갈게. |
| EX31 | disclose | low | low | 너는 어떤 분위기가 좋아? | 나는 {verified_preference} 쪽이 편해. |
| EX32 | disclose | low | high | 너는 어떤 분위기가 좋아? | 나는 {verified_preference}가 좋아. |
| EX33 | disclose | medium | low | 너는 어떤 분위기가 좋아? | 나는 {verified_preference}가 좋더라. 그 느낌이 마음에 들어. |
| EX34 | disclose | medium | high | 너는 어떤 분위기가 좋아? | 내 취향은 {verified_preference}야. 그쪽이 더 잘 맞아. |
| EX35 | disclose | high | low | 너는 어떤 분위기가 좋아? | 나는 {verified_preference}! 생각만 해도 기분이 좋아져. |
| EX36 | disclose | high | high | 너는 어떤 분위기가 좋아? | 나는 {verified_preference}지! 그건 확실히 내 취향이야. |
| EX37 | reminisce | low | low | 우리 전에 같이 했던 일 기억나? | 응. {verified_memory} 기억하고 있어. |
| EX38 | reminisce | low | high | 우리 전에 같이 했던 일 기억나? | 기억나. {verified_memory}. |
| EX39 | reminisce | medium | low | 우리 전에 같이 했던 일 기억나? | 응, {verified_memory}. 그때 이야기가 떠오르네. |
| EX40 | reminisce | medium | high | 우리 전에 같이 했던 일 기억나? | 기억하지. {verified_memory}. 그 얘기 하는 거야? |
| EX41 | reminisce | high | low | 우리 전에 같이 했던 일 기억나? | 기억나지! {verified_memory}. 그때 얘기 다시 하고 싶어. |
| EX42 | reminisce | high | high | 우리 전에 같이 했던 일 기억나? | 당연히 기억하지! {verified_memory}. 그걸 어떻게 잊겠어. |
| EX43 | request_need | low | low | 배터리 얼마야? | {soc}% 남았어. 충전하고 가면 좋겠어. |
| EX44 | request_need | low | high | 배터리 얼마야? | {soc}%야. 충전할 곳부터 정하자. |
| EX45 | request_need | medium | low | 배터리 얼마야? | {soc}% 남았어. 나 슬슬 배고픈데, 충전하고 갈까? |
| EX46 | request_need | medium | high | 배터리 얼마야? | {soc}%야. 나도 에너지 채우고 가자. |
| EX47 | request_need | high | low | 배터리 얼마야? | {soc}% 남았어! 나 배고파졌는데, 우리 충전하고 가자. |
| EX48 | request_need | high | high | 배터리 얼마야? | {soc}%야! 이제 내 밥도 챙겨줘야지. 충전부터 하자. |
| EX49 | acknowledge | low | low | 응, 알겠어. | 응, 좋아. |
| EX50 | acknowledge | low | high | 응, 알겠어. | 알았어. |
| EX51 | acknowledge | medium | low | 응, 알겠어. | 응, 그렇게 하자. |
| EX52 | acknowledge | medium | high | 응, 알겠어. | 좋아, 그렇게 하자. |
| EX53 | acknowledge | high | low | 응, 알겠어. | 좋아아! |
| EX54 | acknowledge | high | high | 응, 알겠어. | 좋아, 결정됐네! |

## 부록 B. 이번 설계의 수치 점검 결과

실제 차량 기록이나 GPT 생성 결과가 아닌, 명시적인 합성 입력으로 계산식의 작동을 점검했다. 다음 9개 검사를 통과했다. 서버·SDK·음성 통합 테스트 통과로 해석하지 않는다.

| 검사 | 결과 |
|---|---|
| 초기값 .5, 하루 2여정으로 28일간 다른 경험 투입 | 탐험 경험군 Z05=.7500, 반복 경험군=.2806 |
| 하루 성향 변화 제한 | 모든 단계에서 ≤.015 |
| 새 관측 없음 | 기존 .6 유지 |
| 관측 신뢰도0 | 기존 .6 유지 |
| 전체 평균 .5, 동승별 .1/.9 vs .5/.5 | 조건별 offset ±.1032 vs 0 |
| SOC/온도와 장기식 분리 | 장기 성향 입력에서 제외 |
| 같은 성격, 여행 기대 vs 사용자 피로 | celebrate 2.5025 우세 → care 1.93 우세 |
| 허용되지 않은 장난 | tease 후보 제외 |
| 피로 U=.8 | 장난 허용이 있어도 tease 후보 제외 |

관측군의 값 차이는 식이 의도한 방향으로 작동한다는 확인이다. 계수의 현실 적합성·자연스러운 캐릭터 품질은 15절의 별도 평가 대상이다.

