# VPE 실측 데이터 연결 표 (2026-11-11)

이 표는 Pleos Vehicle SDK v2.3.2를 기준으로 **공개 API 존재 여부**, **VPE 앱 코드에서 호출하는지**, **Pleos AVD 실수신이 검증됐는지**, **성격/감정 계산에 들어가는지**를 따로 정리한다.

| 신호 | 코드 호출 | 2026-11-09 Pleos AVD 실수신 | HPE 적용 |
| --- | --- | --- | --- |
| VIN | `CarInfo.getVINInfo` | VIN_OK | SHA-256 차량 키 → 차량별 고유 시드 |
| 모델 연도 | 수신한 VIN 10번째 문자 해석(직접 year getter 아님) | 별도 연도 결과 미검증 | 현대 VIN에서 연도 추정 성공 시 연식 → 자신감·신중함 |
| 누적 거리 | `Odometer.getCurrentDistanceDriven`, listener | ODO_OK: 0.0 km | 경험·장기 성격과 기억 |
| EV SOC | `EvBattery.getEvBatteryStateOfCharge`, listener | EV_OK: 100% | 에너지 욕구·긴장 등 일시적 감정 |
| 연료 잔량 | `Fuel.getFuelPercent`, listener | 해당 EV AVD 미지원 | 수신 시 에너지 감정 |
| 실내 현재 온도 | `HVAC.getHvacTemperatureCurrent` | 미검증 | 목표 온도와 함께 수신할 때만 더위·추위 |
| 공조 목표 온도 | `HVAC.getHvacTemperature` | 미검증 | 현재 온도와 함께 수신할 때만 더위·추위 |
| 공조 전원 | `HVAC.getHvacPowerState` | 미검증 | 컨텍스트/진단 |
| EV 충전 스위치 | `EvBattery.getEvChargeSwitchState` | 미검증 | 충전 관련 안도감 (실제 충전 중이라고 단정하지 않음) |
| 트립 거리 | `Odometer.getTripCumulativeDrivingDistance` | 미검증 | SDK 원값 표시·전달 (단위 확인 전 성격 계산에 미사용) |
| 트립 시간 | `Odometer.getTripCumulativeDrivingTime` | 미검증 | SDK 원값 표시·전달 (단위 확인 전 성격 계산에 미사용) |
| 드라이빙 모드 | `DrivingMode.getCurrentDrivingModeState` | 미검증 | SDK 응답 표시·GPT 참고 |
| 워셔액 부족 경고 | `Safety.hasLowWasherFluidWarning` | 미검증 | 실제 true 수신 시 경고 우선 표현 |
| 엔진오일 상태 | `Safety.getEngineOilLevelState` | 미검증 | 원래 SDK 상태 표기, 임의 이상 판정 금지 |
| 브레이크액 상태 | `Safety.getBrakeFluidLevelState` | 미검증 | 원래 SDK 상태 표기, 임의 이상 판정 금지 |

## 아직 실측 입력으로 계산하지 않는 항목
- 여행 경로/목적지/지역, 신규 방문 장소 비율: 별도 NaviHelper 또는 FusedLocation 연동과 이력 집계 필요
- 운행 규칙성·주당 주행 횟수: 트립 이벤트·시간 이력 저장/집계 필요
- 타이어 위치별 경고, 와이퍼 상태: API 형태·차량 지원 범위 확인 후 추가 필요
- 정비 점수: 연결된 정비 이력 출처 없음
- 주차/속도/기어: 읽기 확인된 Vehicle SDK 어댑터 없음

## 더 이상 실측값으로 보내지 않는 기존 중립 기본값
실제 차량에서는 Legacy VehicleSnapshot이 앱 내부 호환용으로 갖는 `ageYears=10`, `fuelPercent=50`, `maintenanceScore=50`, `explorationRatio=0.5`, `longTripRatio=0.5`를 **GPT의 실제 계측 데이터인 것처럼 전송하지 않는다**. 연식은 SDK VIN에서 추정한 경우에만 전송한다. 시연 모드는 별도 `demo=true`로 식별된다.

## 실제 검증 절차
1. Pleos Connect AVD에서 앱 실행 → `Pleos 차량 연결하기`.
2. **진단**에서 각 항목의 `_OK`, `_ERROR`, `UNSUPPORTED`, `미수신`과 반환값 기록. API 메서드 존재/앱 빌드 성공만으로 실수신 성공으로 판단하지 않는다.
3. Virtual Console에서 지원되는 값 변경 후, 반환값 변경과 성격 보고서의 장기값/일시값을 각각 확인한다.
4. 트립 원값의 반환 타입·단위를 확인하기 전에는 장거리 비율, 주당 활동량 같은 이력을 계산하지 않는다.
5. 동일 차량에서 에너지 잔량만 변경할 때 MBTI가 유지되고, 실제 연식·누적거리로부터 계산되는 장기 특성은 별도로 재현되는지 검증한다.

## 수정할 곳
- `app/src/main/java/com/vpe/hyundai/PleosVehicleClient.kt`: 실제 차량 SDK getters / listeners
- `app/src/main/java/com/vpe/hyundai/MainActivity.kt`: SDK 수신값 합치기, 진단 화면, GPT 컨텍스트
- `app/src/main/java/com/vpe/hyundai/HumanPersonalityEngine.kt`: MBTI + 7개 장기 특성, 감정 및 기억 계산
- `server/index.mjs`: 7개 특성의 연속 점수를 말투 지시로 변환

**중요:** 이 문서는 소스 코드와 이전 실제 수신 로그를 기준으로 한다. 새로 연결한 API들의 컴파일 및 Pleos AVD 실수신 결과는 아직 별도 확인이 필요하다.
