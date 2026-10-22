# VA(MSENS) SR 처리 기록

AIG VA 시스템 SR의 **상세 처리 내역**을 보관하는 폴더입니다.
접수·상태·통계는 monday.com `인티큐브 서비스관리 > IT서비스요청` 보드가 기준이고, 이 폴더는 분석 과정·근거·조치를 남깁니다.

## SR 목록

| 등록일 | monday SR ID | 제목 | 상태 | 원인/결론 (한 줄) | 기록 |
|---|---|---|---|---|---|
| 2026-10-19 | [2884222811](https://inticube.monday.com/boards/1936069786/pulses/2884222811), [2883974772](https://inticube.monday.com/boards/1936069786/pulses/2883974772) | Auto-pass 표본점검 특이사항 (동일고객 3건 / 준수문장 No data) | 분석중 | ① 녹취 TPANO 복수 태깅 + 늦게 들어온 콜 'B' 처리(추정) ② SttEncryptWebAction 행 단위 예외 미처리(추정) | [SR-20261019-01](SR-20261019-01_AutoPass_표본점검.md) |
| 2026-10-20 | [2884310926](https://inticube.monday.com/boards/1936069786/pulses/2884310926) | 다른 구간과 매칭 및 동일 문장 매칭 건 분석 | 처리중 | - | 기록 미작성 |

> 상태 값은 monday.com 작업상태와 맞춤: 접수 / 처리중 / 재검토 / 완료대기 / 완료

## 기록 규칙

1. **파일명**: `SR-YYYYMMDD-NN_핵심키워드.md` (YYYYMMDD = 접수일, NN = 당일 순번)
2. **머리말(front matter)** 필수 항목: `sr_id`, `monday_sr`(ID·URL), `status`, `received`, `category`, `keywords`, `design_no`, `related_commit`, `updated`
3. **본문 목차 고정**: 요청 요약 → 진행 로그 → 분석(쿼리·소스 근거) → 원인(확정/추정 구분) → 조치안 → 회신 → 소스 반영 이력 → 남은 일
4. **개인정보**: 고객명·연락처·계좌 등은 기록하지 않음. 설계번호·UCID만 사용
5. **연결**
   - monday.com SR의 `형상소스 Link` 컬럼 ← 이 폴더의 기록 파일 GitHub URL
   - 소스 변경 커밋 메시지 첫머리 ← `[SR-<monday SR ID>]`
6. 새 SR은 [_TEMPLATE.md](_TEMPLATE.md)를 복사해서 시작

## 검색 방법

| 찾는 것 | 방법 |
|---|---|
| 설계번호·UCID·테이블·클래스명 | GitHub 저장소 검색 또는 VS Code 폴더 검색 (예: `942606887558`, `VA_FLAG`, `GetRTinfoWebAction`) |
| 특정 SR 관련 소스 변경 | `git log --grep="SR-2884222811"` |
| 유형별 SR | 머리말 `category` / `keywords` 검색 (예: `category: [분석문의`) |
| 비슷한 과거 SR | Claude 프로젝트 "AIG VA 프로젝트 솔루션 설명서"에서 자연어 질문 (기록 사본이 `claude/SR/`에 있음) |
| 상태·건수·통계 | monday.com IT서비스요청 보드 |
