# 현대자동차 페르소나 프로젝트: 입력 데이터 전체 목록

확인일: 2026-11-14 · Vehicle SDK 2.3.2 · 앱 기준 커밋 a1014d6bc7def5efab01fe9996828dacb085fb4d

## 확인 범위

공식 Maven의 Vehicle-2.3.2.aar 내부 공개 API와 현재 PleosVehicleClient.kt를 대조했다. 20개 도메인에 중복 메서드명을 합친 조회 항목 108종이 있다. 이 중 현재 어댑터에 조회 코드가 있는 항목은 20종이다. 이 숫자는 108종 모두에서 실차/에뮬레이터 수신이 완료되었다는 의미가 아니다. 좌석·창문·문·바퀴별 입력은 지원 영역 수에 따라 값 개수가 늘어난다. 온도 조회의 단위 지정 오버로드는 중복 집계하지 않았다. checkCapabilities, 제어(set/move), 변경 구독(register) 자체는 센서 항목 수에 포함하지 않았다.

표의 활용은 확장 설계안이다. 현재 알고리즘에 모든 활용이 구현되었다는 뜻이 아니다. 장기 특성은 유효한 관측을 시간순으로 모아 집계하고, 현재 상태는 매 대화 시점의 값으로 따로 계산한다.

## SDK 원시 조회 항목 108종

### Odometer — 9종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 차량 누적 주행거리 | `getCurrentDistanceDriven` | Float | 차량 전체 | 조회 코드 있음 | 장기 운행 패턴 / 현재 여정 상태 | 차량 누적값과 앱이 관찰한 기간의 증가량을 구분한다. |
| 현재 트립 주행시간 | `getTripCurrentDrivingTime` | Integer | 차량 전체 | 추가 연결 | 장기 운행 패턴 / 현재 여정 상태 | — |
| 누적 트립 주행시간 | `getTripCumulativeDrivingTime` | Integer | 차량 전체 | 조회 코드 있음 | 장기 운행 패턴 / 현재 여정 상태 | — |
| 주유 이후 주행시간 | `getTripSinceRefillDrivingTime` | Integer | 차량 전체 | 추가 연결 | 장기 운행 패턴 / 현재 여정 상태 | — |
| 현재 트립 주행거리 | `getTripCurrentDrivingDistance` | Float | 차량 전체 | 추가 연결 | 장기 운행 패턴 / 현재 여정 상태 | — |
| 순간 주행거리 항목 | `getTripInstantaneousDrivingDistance` | Float | 차량 전체 | 추가 연결 | 장기 운행 패턴 / 현재 여정 상태 | 정의·단위·갱신 주기를 확인한 뒤 활용한다. 실시간 속도 센서값으로 취급하지 않는다. |
| 누적 트립 주행거리 | `getTripCumulativeDrivingDistance` | Float | 차량 전체 | 조회 코드 있음 | 장기 운행 패턴 / 현재 여정 상태 | — |
| 주유 이후 주행거리 | `getTripSinceRefillDrivingDistance` | Float | 차량 전체 | 추가 연결 | 장기 운행 패턴 / 현재 여정 상태 | — |
| 남은 주행 가능 거리 | `getRangeRemainingDistance` | Float | 차량 전체 | 조회 코드 있음 | 장기 운행 패턴 / 현재 여정 상태 | — |

### Fuel — 5종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 순간 연비 | `getInstantaneousFuelEconomy` | Float | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 보충·소비 패턴 | — |
| 보충 이후 연비 항목(SinceCharge) | `getSinceChargeFuelEconomy` | Float | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 보충·소비 패턴 | 메서드명이 SinceCharge이다. 연료 보충 기준과 리셋 규칙을 확인해 명명한다. |
| 누적 연비 | `getCumulativeFuelEconomy` | Float | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 보충·소비 패턴 | — |
| 현재 연비 | `getCurrentFuelEconomy` | Float | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 보충·소비 패턴 | — |
| 연료 잔량 비율 | `getFuelPercent` | Integer | 차량 전체 | 조회 코드 있음 | 단기 에너지 상태 / 장기 보충·소비 패턴 | — |

### EvBattery — 10종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 순간 전비 | `getInstantaneousEvEfficiency` | Float | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 충전·소비 패턴 | — |
| 충전 이후 전비 | `getSinceChargeEvEfficiency` | Float | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 충전·소비 패턴 | — |
| 누적 전비 | `getCumulativeEvEfficiency` | Float | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 충전·소비 패턴 | — |
| 현재 전비 | `getCurrentEvEfficiency` | Float | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 충전·소비 패턴 | — |
| 배터리 충전율(SOC) | `getEvBatteryStateOfCharge` | Integer | 차량 전체 | 조회 코드 있음 | 단기 에너지 상태 / 장기 충전·소비 패턴 | — |
| 배터리 레벨 별도 수치 | `getEvBatteryLevel` | Float | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 충전·소비 패턴 | SOC와 별도 API이다. Float를 그대로 %로 해석하지 않는다. |
| 충전 전류 제한값 | `getEvChargeCurrentDrawLimit` | Float | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 충전·소비 패턴 | 실제 측정 전류가 아니라 제한값이다. |
| 충전구 열림 여부 | `isEvChargePortOpen` | Boolean | 차량 전체 | 추가 연결 | 단기 에너지 상태 / 장기 충전·소비 패턴 | 충전구 개방 상태이며 충전 진행 여부와 구분한다. |
| 충전 스위치 상태 | `getEvChargeSwitchState` | Boolean | 차량 전체 | 조회 코드 있음 | 단기 에너지 상태 / 장기 충전·소비 패턴 | 충전 스위치 값이다. 실제 에너지 유입 확정값으로 단정하지 않는다. |
| 배터리 용량 | `getEvBatteryCapacity` | Float | 차량 전체 | 조회 코드 있음 | 단기 에너지 상태 / 장기 충전·소비 패턴 | 용량 단위를 확인한다. 용량만으로 배터리 건강도(SOH)를 만들지 않는다. |

### DrivingMode — 3종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 크루즈 설정 속도 | `getCruiseControlTargetSpeed` | Float | 차량 전체 | 조회 코드 있음 | 장기 운전 설정 패턴 / 현재 운전 맥락 | 크루즈 목표 속도이며 실제 차량 속도와 구분한다. |
| 크루즈 상태 | `getCruiseControlState` | CruiseControl | 차량 전체 | 조회 코드 있음 | 장기 운전 설정 패턴 / 현재 운전 맥락 | — |
| 주행 모드 | `getCurrentDrivingModeState` | DrivingModeType | 차량 전체 | 조회 코드 있음 | 장기 운전 설정 패턴 / 현재 운전 맥락 | — |

### Brake — 1종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 브레이크 페달 상태 | `getCurrentBrakePedalStatus` | Boolean | 차량 전체 | 추가 연결 | 현재 운전 맥락 / 누적 제동 이벤트 | Boolean 상태이다. 제동 압력·강도·급제동 여부를 직접 제공하지 않는다. |

### Gear — 1종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 선택 기어 상태 | `getCurrentGearSelectionState` | VehicleGear | 차량 전체 | 추가 연결 | 현재 운전 맥락 / 여정 구분 보조 | — |

### SteeringWheel — 2종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 핸들 열선 상태/단계 | `getHvacSteeringWheelHeatState` | Integer | 차량 전체 | 추가 연결 | 현재 운전 맥락 / 열선 사용 취향 | — |
| 현재 핸들 조향각 | `getCurrentSteeringWheelAngle` | Float | 차량 전체 | 추가 연결 | 현재 운전 맥락 / 열선 사용 취향 | 조향각이다. 속도·경로 없이 공격적 운전/위험 운전 점수로 바꾸지 않는다. |

### TurnSignal — 1종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 방향지시등 상태 | `getCurrentTurnSignalState` | TurnSignalValue | TurnSignalValue | 추가 연결 | 현재 운전 맥락 | 켜짐·꺼짐만으로 적절한 방향지시등 사용률을 판정하지 않는다. |

### HVAC — 14종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 에어컨 상태 | `getHvacAcState` | Boolean | SeatArea | 추가 연결 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 자동 공조 상태 | `getHvacAutoState` | Boolean | SeatArea | 추가 연결 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 자동 내기순환 상태 | `getHvacAutoRecirculationState` | Boolean | SeatArea | 추가 연결 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 창문 성에 제거 상태 | `getHvacDefrosterState` | Boolean | WindowArea | 추가 연결 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 듀얼 공조 상태 | `getHvacDualState` | Boolean | SeatArea | 추가 연결 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 송풍 방향 | `getHvacFanDirection` | FanDirectionType | SeatArea | 추가 연결 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 풍량 단계 | `getHvacFanSpeed` | Integer | SeatArea | 조회 코드 있음 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 최대 냉방 상태 | `getHvacMaxAcState` | Boolean | SeatArea | 추가 연결 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 최대 성에 제거 상태 | `getHvacMaxDefrostState` | Boolean | SeatArea | 추가 연결 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 공조 전원 | `getHvacPowerState` | Boolean | SeatArea | 조회 코드 있음 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 내기순환 상태 | `getHvacRecirculationState` | Boolean | SeatArea | 추가 연결 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 공기청정 상태 | `getHvacAirCleanerState` | Boolean | SeatArea | 추가 연결 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 설정 온도 | `getHvacTemperature` | Float | SeatArea | 조회 코드 있음 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | — |
| 현재 온도 | `getHvacTemperatureCurrent` | Float | SeatArea | 조회 코드 있음 | 단기 온도·쾌적 상태 / 장기 실내 설정 취향 | 단위 지정 오버로드를 하나의 입력 항목으로 합산했다. |

### Seat — 15종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 좌석 점유 여부 | `getCurrentSeatOccupancy` | Boolean | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | 좌석별 점유 상태이다. 사람의 나이·성별·관계를 나타내지 않는다. |
| 헤드레스트 앞뒤 위치 | `getCurrentSeatHeadrestForeAftPosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 헤드레스트 높이 | `getCurrentSeatHeadrestHeightAftPosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 좌석 높이 | `getCurrentSeatHeightPosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 허리받침 앞뒤 위치 | `getCurrentSeatLumbarForeAftPosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 허리받침 측면 지지 위치 | `getCurrentSeatLumbarSideSupportPosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 좌석 기울기 | `getCurrentSeatTiltPosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 좌석 깊이 | `getCurrentSeatDepthPosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 좌석 앞뒤 위치 | `getCurrentSeatForeAftPosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 헤드레스트 각도 | `getCurrentSeatHeadrestAnglePosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 등받이 각도 | `getCurrentSeatBackrestAnglePosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 안전벨트 체결 여부 | `getCurrentSeatBeltBuckledState` | Boolean | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 안전벨트 높이 | `getCurrentSeatBeltHeightPosition` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |
| 좌석 온도/열선 설정값 | `getHvacSeatTemperature` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | Integer 설정값이다. 실내 실제 온도(°C)와 구분한다. |
| 좌석 통풍 상태/단계 | `getHvacSeatVentilationState` | Integer | SeatArea | 추가 연결 | 동승 맥락 / 장기 실내 설정 취향 | — |

### Window — 6종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 창문 잠금 | `getCurrentWindowLockState` | Boolean | WindowArea | 추가 연결 | 단기 실내 상태 / 장기 개방·차광 취향 | — |
| 창문 위치/개방 상태 | `getCurrentWindowPosition` | Integer | WindowArea | 추가 연결 | 단기 실내 상태 / 장기 개방·차광 취향 | 지원 범위와 값 의미에 맞춰 정규화한다. |
| 블라인드 위치 | `getCurrentWindowBlindPosition` | Integer | WindowArea | 추가 연결 | 단기 실내 상태 / 장기 개방·차광 취향 | — |
| 스마트글라스 상태 | `getCurrentSmartGlassState` | Boolean | 차량 전체 | 추가 연결 | 단기 실내 상태 / 장기 개방·차광 취향 | — |
| 스마트글라스 지원 설정 | `getSmartGlassConfig` | SmartGlassConfig | WindowArea | 추가 연결 | 단기 실내 상태 / 장기 개방·차광 취향 | 기능 설정 메타데이터. 현재 스마트글라스 단계와 구분한다. |
| 스마트글라스 단계 | `getCurrentSmartGlassLevel` | Integer | WindowArea | 추가 연결 | 단기 실내 상태 / 장기 개방·차광 취향 | — |

### Door — 3종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 문 잠금 상태 | `getDoorLockState` | Boolean | DoorArea | 추가 연결 | 탑승·하차 맥락 | — |
| 글러브박스 잠금 상태 | `getCurrentGloveBoxLockState` | Boolean | 차량 전체 | 추가 연결 | 탑승·하차 맥락 | — |
| 문 위치/개방 상태 | `getCurrentDoorPosition` | Integer | DoorArea | 추가 연결 | 탑승·하차 맥락 | 값 범위는 해당 환경의 capability로 확인한다. 임의로 개방 퍼센트로 바꾸지 않는다. |

### Light — 19종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 독서등 스위치 | `getReadingLightSwitch` | LightSwitch | SeatArea | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 독서등 실제 점등 상태 | `getCurrentReadingLightState` | LightState | SeatArea | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 실내등 스위치 | `getCabinLightSwitch` | LightSwitch | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 실내등 실제 점등 상태 | `getCurrentCabinLightState` | LightState | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 상향등 실제 점등 상태 | `getCurrentHighBeamLightState` | LightState | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 상향등 스위치 | `getHighBeamLightSwitch` | LightSwitch | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 하향등 스위치 | `getLowBeamLightSwitch` | LightSwitch | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 하향등 실제 점등 상태 | `getCurrentLowBeamLightState` | LightState | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 후미등 실제 점등 상태 | `getCurrentTailLampLightState` | LightState | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 후미등 스위치 | `getTailLampLightSwitch` | LightSwitch | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 전방 안개등 실제 점등 상태 | `getCurrentFrontFogLightState` | LightState | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 전방 안개등 스위치 | `getFrontFogLightSwitch` | LightSwitch | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 후방 안개등 실제 점등 상태 | `getCurrentRearFogLightState` | LightState | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 후방 안개등 스위치 | `getRearFogLightSwitch` | LightSwitch | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 앰비언트 밝기 | `getAmbientLightBrightness` | Integer | SeatArea | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 앰비언트 색상 코드 | `getAmbientLightBrightnessColor` | Integer | SeatArea | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | Integer 색상 코드이다. 색상 인코딩을 확인한다. |
| 앰비언트 스위치 | `getAmbientLightSwitch` | LightSwitch | SeatArea | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 앰비언트 실제 점등 상태 | `getAmbientLightState` | LightState | SeatArea | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |
| 주행 모드 연동 색상 자동 변경 여부 | `getAmbientColorAutoByDriveModeEnabled` | Boolean | 차량 전체 | 추가 연결 | 현재 조명 맥락 / 장기 실내 조명 취향 | — |

### Wiper — 2종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 와이퍼 실제 작동 상태 | `getCurrentWiperState` | WiperState | WindowArea | 추가 연결 | 현재 운전 맥락 | 비뿐 아니라 세정 등으로도 작동한다. 날씨 확정 정보로 쓰지 않는다. |
| 와이퍼 스위치 | `getCurrentWiperSwitch` | WiperSwitch | WindowArea | 추가 연결 | 현재 운전 맥락 | — |

### SideMirror — 1종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 사이드미러 열선 상태/단계 | `getHvacSideMirrorHeatState` | Integer | MirrorArea | 추가 연결 | 현재 열선 사용 맥락 | — |

### Tire — 2종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 저압 관련 수치 | `getLowTirePressure` | Float | WheelArea | 추가 연결 | 안전 안내 | Float 반환값이다. Boolean 저압 경고나 임계값으로 임의 확정하지 않고 세부 의미를 확인한다. |
| 타이어 공기압 | `getTirePressure` | Float | WheelArea | 추가 연결 | 안전 안내 | — |

### Safety — 9종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 엔진오일 레벨 상태 | `getEngineOilLevelState` | OilLevelState | 차량 전체 | 조회 코드 있음 | 안전 안내 | — |
| 브레이크액 레벨 상태 | `getBrakeFluidLevelState` | OilLevelState | 차량 전체 | 조회 코드 있음 | 안전 안내 | — |
| 변속기 오일 레벨 상태 | `getTransmissionOilLevelState` | OilLevelState | 차량 전체 | 추가 연결 | 안전 안내 | — |
| 워셔액 부족 경고 | `hasLowWasherFluidWarning` | Boolean | 차량 전체 | 조회 코드 있음 | 안전 안내 | — |
| 좌석 에어백 전개 여부 | `hasSeatAirbagBeenDeployed` | Boolean | SeatArea | 추가 연결 | 안전 안내 | — |
| 출력 저하 경고 | `hasPowerDownWarningIndicator` | Boolean | 차량 전체 | 추가 연결 | 안전 안내 | — |
| 좌석 에어백 활성화 여부 | `hasSeatAirbagEnabled` | Boolean | SeatArea | 추가 연결 | 안전 안내 | — |
| 안전벨트 미체결 경고 | `hasSeatBeltNotBuckledWarning` | Boolean | SeatArea | 추가 연결 | 안전 안내 | — |
| 계기판 표시등/경고 목록 | `getTelltalesInfo` | Telltales | 차량 전체 | 추가 연결 | 안전 안내 | 목록의 각 항목: type, state, color, priority, warningCount. 중복 경고와 중복 집계하지 않는다. |

### Toll — 1종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| ETCS 음성 안내 볼륨 | `getCurrentEtcsVoiceGuidanceVolume` | Integer | 차량 전체 | 추가 연결 | 안내 음량 취향 | — |

### Display — 1종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| 온도 표시 단위 | `getCurrentTemperatureDisplayUnit` | TemperatureUnit | 차량 전체 | 추가 연결 | 해석용 메타데이터 | — |

### CarInfo — 3종

| 항목 | 정확한 API | 반환 형식 | 영역 | 현재 앱 | 활용 | 해석 메모 |
|---|---|---|---|---|---|---|
| VIN | `getVINInfo` | String | 차량 전체 | 조회 코드 있음 | 차량 식별 / 지원 범위 메타데이터 | 차량별 프로필 연결 키. 현재 코드는 해시를 사용하며 모델연도는 VIN에서 추정한다. |
| CRN | `getCRNInfo` | String | 차량 전체 | 조회 코드 있음 | 차량 식별 / 지원 범위 메타데이터 | 현재 코드는 VIN이 비어 있거나 실패하면 대체 식별자로 조회한다. |
| 차량 기능 지원 정보 | `getCapabilityInfo` | Map<String, CapabilityConfig> | 차량 전체 | 추가 연결 | 차량 식별 / 지원 범위 메타데이터 | 지원 여부·범위·접근 방식 메타데이터. 성격 점수에 넣는 센서값이 아니다. |

## 별도 SDK로 확장할 입력

Vehicle SDK의 108종과 별도로 계산한다. 현재 Vehicle 어댑터의 구현으로 간주하지 않는다.

| 출처 | 입력/기능 | 만들 수 있는 장기 특징 |
|---|---|---|
| FusedLocation SDK 1.1.2 | 위도, 경도, 고도, 진행방향, 신뢰도, 타임스탬프 | 방문 지점, 활동 반경, 반복 경로, 새 장소 비율, 지역별 체류, 이동 시간대 |
| NaviHelper SDK 3.0.5 | 목적지 확인, 경유지 조회, 경로 탐색·운행 상태 이벤트 | 반복 목적지, 여행 경로, 경유 습관; 구체 이벤트 필드는 연동 시 해당 API와 대조 |
| 앱 자체 기록 | 세션 시작·종료 시각, 사용자 발화, AI 발화, 끼어들기, 질문 반응, 캐릭터 피드백 | 대화 빈도, 응답 길이 취향, 농담 수용, 먼저 말 걸기 선호 |
| 추가 지도/장소 데이터 | 좌표→도로 분류, 장소 카테고리, 행정구역 | 도심/고속도로 비율, 방문 장소 카테고리, 지역 맥락 |

FusedLocation 공식 문서에 명시된 LocationData 필드만 적었다. 실제 속도·가속도·제동 강도·음악·소음·정비 이력·직접 모델연도 조회는 이번 Vehicle 2.3.2 바이너리의 위 108종에서 확인되지 않는다. 추가 데이터 경로 또는 계산이 필요한 항목으로 관리한다.

## 원시값에 항상 붙여서 저장할 항목

익명 차량/프로필 키, signal 이름, 원본 값, 단위, 영역, 원본 관측 시각, 앱 수신 시각, 유효 여부, 지원 여부, 오류, 데이터 출처(실차/에뮬레이터/테스트), 여정 ID. 미수신을 0으로 저장하지 않고, 오래된 값을 현재 상태로 재사용하지 않는다. 앱 세션과 실제 여정도 구분한다.

## 축적 후 계산할 장기 입력: 제안

아래는 SDK가 완성된 값으로 주는 목록이 아니라, 앱이 원시 기록으로 계산할 특징이다. 최초 설치 이전 과거 이력은 현재 누적 계기값만으로 복원하지 않는다.

| 묶음 | 원시 입력 | 파생 특징 |
|---|---|---|
| 운행량 | 누적/트립 거리·시간 + 시각 | 일·주간 이동거리, 1회 평균/중앙값 거리, 1회 주행시간, 짧은/긴 여정 비율, 최근 사용량 추세 |
| 생활 리듬 | 여정 시작·종료 + 시각 | 주행 빈도, 출발 시간대 분포, 야간 운행 비율, 평일/주말 차이, 출발 시각 규칙성, 휴차 간격 |
| 운전 설정 | 주행 모드 + 크루즈 + 시각 | 모드별 사용시간 비율, 모드 전환 빈도, 크루즈 사용시간 비율, 크루즈 설정 속도 분포 |
| 운전 동작 | 브레이크 Boolean + 조향각 + 기어 + 방향지시등 | 제동 이벤트 빈도, 조향각 변화량, 기어 전환 패턴; 도로·속도 맥락과 함께 해석 |
| 에너지 관리 | 연료/SOC 변화 + 충전 스위치/충전구 + 시각 | 잔량 분포, 보충 전 잔량, 보충 간격, 저잔량 체류, 보충 이벤트 후 회복량; 스위치 ON만으로 충전 완료 판정하지 않음 |
| 에너지 효율 | 연비/전비 + 거리/시간 | 동일 차량의 기간별 효율 추세, 여정 간 변동; 단위·환경·차종 차이를 반영 |
| 동승 | 좌석별 점유 + 여정 | 혼자/여럿 탄 여정 비율, 동승 좌석 구성, 동승 시간대; 사람의 성격으로 바로 변환하지 않음 |
| 실내 취향 | 설정 온도/현재 온도 + 풍량 + 열선/통풍 + 창문 | 선호 온도 분포, 열선·통풍 사용률, 환기 패턴, 반복 설정의 안정성 |
| 분위기 취향 | 앰비언트 색상/밝기 + 실내등 + 블라인드 | 자주 쓰는 색상, 밝기 분포, 설정 변경 빈도, 차광 사용 패턴 |
| 공간 경험 | 위치 시계열 + 선택적 지도 데이터 | 활동 반경, 방문 다양성, 처음 간 장소 비율, 경로 반복성, 장거리 여행 빈도 |
| 대화 관계 | 앱 대화 기록 + 명시적 피드백 | 대화 빈도, 질문 수용률, 답변 길이 취향, 농담/직설/먼저 말 걸기 선호 |

## 장기 페르소나와 단기 상태의 분리

| 같은 원시 데이터 | 이번 대화에만 적용 | 장기간 누적 후 적용 |
|---|---|---|
| SOC/연료 | 현재 에너지 부족을 캐릭터의 배고픔 표현으로 사용 | 반복적인 보충 주기와 잔량 유지 패턴 |
| 현재/설정 온도 | 현재 춥다·덥다·쾌적하다 표현 | 자주 선택하는 온도와 쾌적 설정 취향 |
| 현재 트립 시간 | 이번 여정의 피로 표현 | 짧은 이동/긴 여행 중심의 생활 패턴 |
| 좌석 점유 | 지금 동승자가 있는 대화 맥락 | 혼자/동승 운행 경험의 누적 분포 |
| 주행 모드 | 현재 주행 상황에 맞는 발화 길이·타이밍 | 기간별 모드 사용 비중과 변화 |

사용자가 직접 말한 “나 배고파/추워”는 사용자 현재 상태로, 연료/실내온도에서 만든 차량 캐릭터의 상태와 별도 필드에 저장한다. 차량 경고는 별도 안내 항목으로 처리한다.

장기 특징을 캐릭터로 바꾸는 매핑은 제품의 창작 규칙이다. 차량 기록으로 운전자의 MBTI를 진단한다는 의미가 아니다. 말투 차별화는 이후 캐릭터별 발화 규칙·대화 예시·상황별 평가로 확인한다.

## 출처

- [공식 Vehicle SDK 2.3.2 AAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.aar)
- [현재 앱의 Vehicle 어댑터](https://github.com/zoey10px-lgtm/VPE-Hyundai-/blob/a1014d6bc7def5efab01fe9996828dacb085fb4d/app/src/main/java/com/vpe/hyundai/PleosVehicleClient.kt)
- [Vehicle SDK 공식 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [FusedLocation 공식 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)
- [NaviHelper 공식 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)

AAR SHA-256: `e11fdfe498cad3ecc0d5a2a6992b0c4a4d135195f1455018127bc069fd5ef937`
