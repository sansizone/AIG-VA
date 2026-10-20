package xcron.com.webaction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import sens.util.SFTPUtil;
import wfm.com.util.AES256Cipher;
import jedix.xwing.action.XwingWebAction;

import com.hansol.audio.util.PropertyUtil;
import com.locus.jedi.log.IVRLogger;
import com.locus.jedi.service.sql.SQLParam;
import com.locus.jedi.service.sql.SQLServiceManager;
import com.locus.jedi.transfer.ListParam;
import com.locus.jedi.util.Code;
import com.locus.jedi.util.CodeUtil;
import com.locus.jedi.waf.action.WebActionException;
import com.locus.jedi.waf.controller.JediRequest;
import com.locus.jedi.waf.controller.JediResponse;

/*
 * 운영에 반영한 22.06.28일을 기준으로 아래 배치가 새벽에 진행된다
 * 1) 하루 전 데이터 가져옴
 * 2) 29일 이전 데이터를 순차적으로 가져옴
 * 
 * 29일 이전의 과거 데이터는 22년 1월 1일까지의 데이터만 가져오고 그 이전 데이터는 파일 생성 및 전송하지 않는다.
 * 파일 생성 시 컬럼 구분자는 '\t' 이다. 
 */
public class SttTransferWebAction extends XwingWebAction {
	
	private static final long serialVersionUID = 1L;

	ListParam SttResultList = new ListParam(new String[] {"RXTX_GB", "UCID", "DURATION", "STT_SP_FLAG", "REG_ID", "REG_DATE", "CHG_ID", "CHG_DATE", "ENC_FLAG", "ENC_R_TIME", "NARRATOR", "STARTTIME", "ENDTIME", "CONTENT"});
	ListParam SttResultList2 = new ListParam(new String[] {"UCID", "CON_ENT_DGN_NO"});
	
	ListParam SttUcidList = new ListParam(new String[] {"UCID"});
	
	Properties prop; 
	Properties prop2;
	
	int start = 0;
	int pageSize = 1000;
	int page = 1;
	
	String standardDate = "20220628"; // 과거 콜 분석을 위한 시작시점(기준점)
	
	/*
	 * STT복호화 및 SFTP 전송 
	 * 26.09.10 from_to Date 입력 수정
	 */
	public void perform(JediRequest req, JediResponse res) throws WebActionException {
		IVRLogger.error("#################SttTransferWebAction Start!!#################");
		
		try {
			/* 0. 환경설정 및 초기화 */
			prop = new Properties(); // sftpServer.properties
			prop = PropertyUtil.loadProperty(System.getProperty("jedi.home")+"/WEB-INF/sftpServer.properties");
			
			prop2 = new Properties(); // conf.properties
			prop2 = PropertyUtil.loadProperty(System.getProperty("jedi.home")+ "/WEB-INF/conf.properties");
			
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
			Calendar minDate = Calendar.getInstance();
			minDate.set(2022, Calendar.JANUARY, 1, 0, 0, 0); // 과거 콜 하한선 제한 (2022.01.01)
			
			/*
			 * 26.09.10 XML 파라미터(from_date, to_date) 가져오기
			 */
			String cmd = req.param.getString("cmd"); // 과거 콜 분석용인지 확인 ('before')
			String fromDateStr = req.param.getString("from_date"); // 시작일 (YYYYMMDD)
			String toDateStr = req.param.getString("to_date");     // 종료일 (YYYYMMDD)
			
			List<String> targetDays = new ArrayList<>();
			
			if (cmd != null && "before".equals(cmd)) {
				// 과거 콜 분석인 경우 필수 파라미터 체크
				if (fromDateStr == null || fromDateStr.isEmpty() || toDateStr == null || toDateStr.isEmpty()) {
					IVRLogger.error("##### [에러] 과거 콜 추출('before') 시 from_date와 to_date 파라미터는 필수입니다.");
					IVRLogger.info("#################SttTransferWebAction END!!#################");
					return;
				}
				
				IVRLogger.error("################# 과거 콜 추출을 시작합니다! [" + fromDateStr + " ~ " + toDateStr + "] #################");
				
				Calendar startCal = Calendar.getInstance();
				startCal.setTime(sdf.parse(fromDateStr));
				
				Calendar endCal = Calendar.getInstance();
				endCal.setTime(sdf.parse(toDateStr));
				
				// 시작일부터 종료일까지 하루씩 증가시키며 대상 날짜 리스트 생성
				while (!startCal.after(endCal)) {
					if (startCal.compareTo(minDate) >= 0) { // 2022년 1월 1일 이후 데이터만 타겟팅
						targetDays.add(sdf.format(startCal.getTime()));
					} else {
						IVRLogger.info("##### [제외] 2022년 01월 01일 이전 날짜는 전송 대상에서 제외됩니다: " + sdf.format(startCal.getTime()));
					}
					startCal.add(Calendar.DATE, 1);
				}
			} else {
				// 일반 호출인 경우 (기본값: 배치 시점 하루 전 STT 가져옴)
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.DATE, -1);
				
				if (cal.compareTo(minDate) >= 0) {
					targetDays.add(sdf.format(cal.getTime()));
				} else {
					IVRLogger.info("##### 과거 콜의 STT 데이터는 2022년 01월 01일 이후 데이터만 전송합니다.");
				}
			}
			
			// 추출할 일자가 없으면 즉시 종료
			if (targetDays.isEmpty()) {
				IVRLogger.info("##### 추출 및 전송 대상 날짜가 존재하지 않습니다.");
				IVRLogger.info("#################SttTransferWebAction END!!#################");
				return;
			}
			
			/* 2. 대상 기간 루프 수행 (하루씩 순차 처리) */
			String filePath = prop.getProperty("stt_sftp_temp_path");
			
			for (String dayStr : targetDays) {
				IVRLogger.error("### 현재 처리 일자 (dayStr) :: " + dayStr);
				
				// ⚠️ [중요] 멤버 변수 오염 및 데이터 중복 축적 방지
				// 루프가 돌 때마다 기존에 쌓인 row들을 초기화해주어야 날짜별 파일이 섞이지 않습니다.
				// 데이터 컴포넌트 구조에 맞춰 반드시 아래처럼 초기화(clear)를 처리해주세요.
				if (SttResultList != null) SttResultList.clear();   // (객체 제공 메서드에 따라 clearRow() 등 변형 가능)
				if (SttResultList2 != null) SttResultList2.clear();
				
				// 임시파일명 구성
				String fileName = prop.getProperty("stt_sftp_fileName_1") + "_" + dayStr + ".txt";
				String fileName2 = prop.getProperty("stt_sftp_fileName_2") + "_" + dayStr + ".txt";
				
				IVRLogger.error("#####SttTransferWebAction filePath: " + filePath + " // fileName1: " + fileName + " // fileName2: " + fileName2);
				
				/* 2-1. 데이터1 :: STT 추출 및 복호화 */
				SQLParam sqlParam1 = new SQLParam();
				sqlParam1.setSqlName("msens.xcron.hansol.stttransferwebAction_1");
				sqlParam1.addValue("dayStr", dayStr);
				SQLParam sqlResult1 = SQLServiceManager.getInstance().execute(sqlParam1);
				
				int totalCnt = Integer.parseInt(sqlResult1.getListParam("msens.xcron.hansol.stttransferwebAction_1").getParam(0).getString("CNT"));
				
				if (totalCnt > 0) {
					int endPage = (int) Math.ceil(totalCnt * 1.0 / pageSize * 1.0);
					IVRLogger.error("#####SttTransferWebAction::totalCnt::" + totalCnt + "//endPage::" + endPage);
					
					for (int page = 1; page <= endPage; page++) {
						int startRow = (page - 1) * pageSize; // ⚠️ 스레드 동시성 버그 방지를 위해 로컬 변수화
						
						IVRLogger.error("#####SttTransferWebAction::msens.xcron.hansol.stttransferwebAction_2::page_[" + page + "/" + endPage + "]");
						SQLParam sqlParam2 = new SQLParam();
						sqlParam2.setSqlName("msens.xcron.hansol.stttransferwebAction_2");
						sqlParam2.addValue("_START", startRow);
						sqlParam2.addValue("_PAGESIZE", pageSize);
						sqlParam2.addValue("dayStr", dayStr);
						SQLParam sqlResult2 = SQLServiceManager.getInstance().execute(sqlParam2);
						
						for (int i = 0; i < sqlResult2.getCount(); i++) {
							String encFlag = sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("ENC_FLAG"); 
							String content = sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("CONTENT"); 
							
							/*
							 * 26.09.10 암호화된 CONTENT를 복호화
							 */
							if ("Y".equals(encFlag) && content != null) {
								byte[] bytes = content.getBytes();
								
								
								IVRLogger.error("#####SttTransferWebAction::bytes.length [content bytes.length: " + bytes.length + "]");
								
								String decryptedContent = decrypt(content);
								
								// 실패 시 ""가 반환되므로 null과 빈 문자열을 동시에 체크합니다.
								if (decryptedContent == null || decryptedContent.isEmpty()) {
									String currentUcid = String.valueOf("UCID");
									
									// 어떤 UCID에서 실패했는지 식별할 수 있도록 로그 기록
									IVRLogger.error("#####SttTransferWebAction::복호화 실패로 인해 해당 자료를 건너뜁니다. [UCID: " + currentUcid + "]");
									continue; // ★ 오류 발생 시 아래 로직을 타지 않고 다음 자료 처리로 이동
								}
								
								content = decryptedContent;
							}
	
							if (content == null || content.isEmpty()) continue;
							
							String[] lines = content.split("\n");
							
							// line 단위로 데이터 파싱 및 저장
							for (int j = 0; j < lines.length; j++) {
								String line = lines[j];
								
								// 방어적 코드: 구분자가 없거나 비정상 데이터 유입 시 ArrayIndexOutOfBoundsException 방지
								String[] bars = line.split("\\|");
								if (bars.length < 2) continue;
								
								String narrator = bars[0];
								String[] tabs = bars[1].split("\t");
								if (tabs.length < 3) continue;
								
								String start_time = tabs[0];
								String end_time = tabs[1];
								String stt_content = tabs[2].trim();
								
								SttResultList.addRow(new Object[]{
										sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("RXTX_GB"),
										sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("UCID"),
										sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("DURATION"),
										sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("STT_SP_FLAG"),
										sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("REG_ID"),
										sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("REG_DATE"),
										sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("CHG_ID"),
										sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("CHG_DATE"),
										sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("ENC_FLAG"),
										sqlResult2.getListParam("msens.xcron.hansol.stttransferwebAction_2").getParam(i).getString("ENC_R_TIME"),
										narrator, start_time, end_time, stt_content
								});
							}
						}
					}
				}
				
				/* 2-2. 데이터2 :: 추출한 UCID와 가입설계번호 조회 */
				SQLParam sqlParam3 = new SQLParam();
				sqlParam3.setSqlName("msens.xcron.hansol.stttransferwebAction_3");
				sqlParam3.addValue("dayStr", dayStr);
				SQLParam sqlResult3 = SQLServiceManager.getInstance().execute(sqlParam3);
				IVRLogger.error("#####SttTransferWebAction::msens.xcron.hansol.stttransferwebAction_3::getCount():" + sqlResult3.getCount());
				
				for (int k = 0; k < sqlResult3.getCount(); k++) {
					SttResultList2.addRow(new Object[]{
							sqlResult3.getListParam("msens.xcron.hansol.stttransferwebAction_3").getParam(k).getString("UCID"),
							sqlResult3.getListParam("msens.xcron.hansol.stttransferwebAction_3").getParam(k).getString("CON_ENT_DGN_NO")
					});
				}
				
				/* 2-3. 임시 파일 생성 및 SFTP 업로드 */
				// 한 날짜가 실패하더라도 다음 날짜 전송에 영향을 주지 않도록 루프 내 제어는 continue 처리
				if (!makeSttFile(filePath, fileName)) {
					IVRLogger.info("#################SttTransferWebAction makeSttFile 실패로 "+ dayStr + "처리를 건너 뜁니다.");
					continue;
				}

				if (!makeSttFile2(filePath, fileName2)) {
					IVRLogger.info("#################SttTransferWebAction makeSttFile2 실패로 "+ dayStr + "처리를 건너 뜁니다.");
					continue;
				}
				
				/* 3. 해당 파일 SFTP 서버에 업로드 */
				if(!transferSttFile(filePath, fileName, fileName2)){
					IVRLogger.info("#################SttTransferWebAction transferSttFile 실패로 "+ dayStr + "처리를 건너 뜁니다.");
					continue;
				}
			}
		} catch (Exception e) {
			IVRLogger.error("#####SttTransferWebAction message = "+e.getMessage());
			IVRLogger.error(e.getStackTrace());
		}
	}

	
	/*
	 * 26.09.10 암호화된 CONTENT를 복호화
	 */
	public String decrypt(String content){
		String decContent = "";
		String aes_key = prop2.getProperty("cipher_key");
		
		try {
			AES256Cipher aes256 = AES256Cipher.getInstance(aes_key);
			decContent = aes256.decrypt(content);
		} catch (Exception e) {
			// e를 인자로 직접 넘겨야 StackTrace가 로그에 온전하게 찍힙니다.
			IVRLogger.error("#####SttTransferWebAction 복호화 중 예외 발생: " + e.getMessage(), e);
		}
		return decContent;
	}
	
	
	/*
	 * STT 복호화 결과(SttResultList)를 파일로 생성 후 임시파일로 저장
	 * 파일 생성에 성공 시 true, 실패 시 flase 반환
	 */
	public boolean makeSttFile(String filePath, String fileName){
		IVRLogger.error("########SttTransferWebAction makeSttFile() 시작");
		
		try{
			/* 폴더 존재여부 확인:: 폴더가 존재하지 않을 경우. 폴더 생성 */
			File folder = new File(filePath);
			if(!folder.exists()){
				if(folder.mkdir())
					IVRLogger.error(filePath + "이 존재하지 않아 폴더를 생성합니다. 폴더 생성 성공");
				else{
					IVRLogger.error(filePath + "이 존재하지 않아 폴더를 생성합니다. 폴더 생성에 실패하였습니다.");
					return false;
				}
			}
			
			
			/* 파일 존재여부 확인:: 파일 생성(파일이 존재하는 경우, 파일 삭제 후 생성) */
			File file = new File(folder, fileName);
			if(file.exists()){
				if(file.delete())
					IVRLogger.error(fileName + "이 이미 존재하여 파일을 삭제합니다. 파일 삭제 성공");
				else{
					IVRLogger.error(fileName + "이 이미 존재하여 파일을 삭제합니다. 파일 삭제에 실패하였습니다.");
					return false;
				}
			}
			if(file.createNewFile())
				IVRLogger.error(fileName + "을 생성합니다. 파일 생성 성공");
			else{
				IVRLogger.error(fileName + "을 생성합니다. 파일 생성에 실패하였습니다.");
				return false;
			}
			
			/* 파일 쓰기 */
			BufferedWriter fw = new BufferedWriter(new FileWriter(file));
			StringBuffer sb = new StringBuffer();
			
			// 컬럼헤더추가
			sb.append("RXTX_GB\tUCID\tDURATION\tSTT_SP_FLAG\tREG_ID\tREG_DATE\tCHG_ID\tCHG_DATE\tENC_FLAG\tENC_R_TIME\tNARRATOR\tSTARTTIME\tENDTIME\tCONTENT\n");
			for(int i=0; i<SttResultList.rowSize(); i++){
				IVRLogger.debug("####UCID::"+SttResultList.getValue(i, "UCID").toString()); // 에러발생 시 로그레벨 낮추고 배치 재실행 후 해당 로그 확인
				
				String rxtx_gb = SttResultList.getValue(i, "RXTX_GB").toString();
				String ucid = SttResultList.getValue(i, "UCID").toString();
				String duration = SttResultList.getValue(i, "DURATION").toString();
				String stt_sp_flag = SttResultList.getValue(i, "STT_SP_FLAG").toString();
				String reg_id = SttResultList.getValue(i, "REG_ID").toString();
				String reg_date = SttResultList.getValue(i, "REG_DATE").toString();
				String chg_id = SttResultList.getValue(i, "CHG_ID").toString();
				String chg_date = SttResultList.getValue(i, "CHG_DATE").toString();
				String enc_flag = SttResultList.getValue(i, "ENC_FLAG").toString();
				String enc_r_time = SttResultList.getValue(i, "ENC_R_TIME").toString();
				String narrator = SttResultList.getValue(i, "NARRATOR").toString();
				String start_time = SttResultList.getValue(i, "STARTTIME").toString();
				String end_time = SttResultList.getValue(i, "ENDTIME").toString();
				String content = SttResultList.getValue(i, "CONTENT").toString();
				
				sb.append(rxtx_gb+"\t");
				sb.append(ucid+"\t");
				sb.append(duration+"\t");
				sb.append(stt_sp_flag+"\t");
				sb.append(reg_id+"\t");
				sb.append(reg_date+"\t");
				sb.append(chg_id+"\t");
				sb.append(chg_date+"\t");
				sb.append(enc_flag+"\t");
				sb.append(enc_r_time+"\t");
				sb.append(narrator+"\t");
				sb.append(start_time+"\t");
				sb.append(end_time+"\t");
				sb.append(content+"\n");
				
				if(sb.length()>0){
					fw.write(sb.toString());
					fw.flush();
					sb.delete(0,sb.length());
				}
				IVRLogger.debug("####UCID::"+SttResultList.getValue(i, "UCID").toString()); // 에러발생 시 로그레벨 낮추고 배치 재실행 후 해당 로그 확인
			}
			
			fw.close();
			
			IVRLogger.error("########SttTransferWebAction makeSttFile() 종료");
			return true;
			
		} catch(IOException e) {
			e.printStackTrace();
			IVRLogger.error("#####SttTransferWebAction message = "+e.getMessage());
			IVRLogger.error(e.getStackTrace());
			return false;
		}
	}

	
	/*
	 * STT ucid,설계번호 결과(SttUcidList)를 파일로 생성 후 임시파일로 저장
	 * 파일 생성에 성공 시 true, 실패 시 flase 반환
	 */
	public boolean makeSttFile2(String filePath, String fileName){
		IVRLogger.error("########SttTransferWebAction makeSttFile2() 시작");
		
		try{
			/* 파일 존재여부 확인:: 파일 생성(파일이 존재하는 경우, 파일 삭제 후 생성) */
			File folder = new File(filePath);
			File file = new File(folder, fileName);
			if(file.exists()){
				if(file.delete())
					IVRLogger.error(fileName + "이 이미 존재하여 파일을 삭제합니다. 파일 삭제 성공");
				else{
					IVRLogger.error(fileName + "이 이미 존재하여 파일을 삭제합니다. 파일 삭제에 실패하였습니다.");
					return false;
				}
			}
			if(file.createNewFile())
				IVRLogger.error(fileName + "을 생성합니다. 파일 생성 성공");
			else{
				IVRLogger.error(fileName + "을 생성합니다. 파일 생성에 실패하였습니다.");
				return false;
			}
			
			/* 파일 쓰기 */
			BufferedWriter fw = new BufferedWriter(new FileWriter(file));
			StringBuffer sb = new StringBuffer();
			
			// 컬럼헤더추가
			sb.append("UCID\tCON_ENT_DGN_NO\n");
			for(int i=0; i<SttResultList2.rowSize(); i++){
				String ucid = SttResultList2.getValue(i, "UCID").toString();
				String con_ent_dgn_no = SttResultList2.getValue(i, "CON_ENT_DGN_NO").toString();
				
				sb.append(ucid+"\t");
				sb.append(con_ent_dgn_no+"\n");
				
				if(sb.length()>0){
					fw.write(sb.toString());
					fw.flush();
					sb.delete(0,sb.length());
				}
			}
			
			fw.close();
			
			return true;
			
		} catch(IOException e) {
			e.printStackTrace();
			IVRLogger.error("#####SttTransferWebAction message = "+e.getMessage());
			IVRLogger.error(e.getStackTrace());
			return false;
		}
	}
	
	
	/*
	 * 임시파일로 저장된 파일을 sftp 전송
	 * 파일 전송에 성공 시 true, 실패 시 flase 반환
	 */
	public boolean transferSttFile(String filePath, String fileName, String fileName2){
		IVRLogger.error("########SttTransferWebAction transferSttFile() 시작");
		
		try {
			/* 접속 정보 가져오기 */
			Map<String, String> sftpInfo = getSFTPInfo();
			String host = sftpInfo.get("host");
			int port = Integer.parseInt(sftpInfo.get("port"));
			String userName = sftpInfo.get("userName");
			String password = sftpInfo.get("password");
			String dir = sftpInfo.get("dir");
			
			if(sftpInfo.size()>0){
				IVRLogger.error("##host == " + host + "// port = " + port + "// dir = " + dir);
				
				/* 계정정보 복호화 후 가져오기 */
				String aes_key = prop2.getProperty("cipher_key");
				AES256Cipher aes256 = AES256Cipher.getInstance(aes_key);
				
				userName = aes256.decrypt(userName); 
				password = aes256.decrypt(password);
				
				/* File(contents_날짜.txt 파일 get */
				File folder = new File(filePath);
				File file = new File(folder, fileName);
				
				/* File2(callId_날짜.txt 파일 get */
				File file2 = new File(folder, fileName2);
				
				
				/* SFTPUtil로 sftp 전송 */
				SFTPUtil util = new SFTPUtil();
				util.init(host,userName, password, port);	//서버연결
				util.upload(dir, file);						//파일 업로드
				util.upload(dir, file2);
				util.disconnection();						//세션종료
			
				IVRLogger.error("########SttTransferWebAction transferSttFile() 종료");
				return true;
			}else{
				IVRLogger.error("#####SttTransferWebAction message = Failed to get sftpInfo");
				return false;
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			IVRLogger.error("#####SttTransferWebAction message = "+e.getMessage());
			IVRLogger.error(e.getStackTrace());
			return false;
		}
	}
	
	/*
	 * 접속할 호스트 정보를 가져옴
	 */
	public Map<String,String> getSFTPInfo(){
		Code[] code = CodeUtil.getCodes("SYS022"); //VA서버도메인정보
		String serverIp;
		String[] temp = null;
		String serverType = "";
		
		Map<String, String> result = new HashMap<String, String>();
		
		String host = prop.getProperty("stt_sftp_host");
		String port = prop.getProperty("stt_sftp_port");
		String userName = prop.getProperty("stt_sftp_user");
		String password = prop.getProperty("stt_sftp_password");
		String dir = prop.getProperty("stt_sftp_dir");
		
		try {
			serverIp = InetAddress.getLocalHost().getHostAddress();
			
			for (int j = 0; code != null && j < code.length; j++) {
				if (!"Y".equalsIgnoreCase(code[j].getUseYn())) {
					continue;
				}
				if(serverIp.equals(code[j].getEtc1())){ 
					temp = code[j].getEtc2().split("\\.");
					break;
				}
			}
			if(temp != null && temp.length > 0){
				serverType = temp[0];
			}
			
			IVRLogger.error("##serverIp == " + serverIp + "// servertype = " + serverType);	
				
			switch (serverType) {
			case "va":
				userName = prop.getProperty("stt_sftp_user");
				password = prop.getProperty("stt_sftp_password");
				dir = prop.getProperty("stt_sftp_dir");
				break;
			case "vadev":
			case "vauat":
				userName = prop.getProperty("stt_sftp_user_dev");
				password = prop.getProperty("stt_sftp_password_dev");
				dir = prop.getProperty("stt_sftp_dir_dev");
				break;
			default:
				userName = prop.getProperty("stt_sftp_user_dev");
				password = prop.getProperty("stt_sftp_password_dev");
				dir = prop.getProperty("stt_sftp_dir_dev");
				break;
			}
			
			result.put("host", host);
			result.put("port", port);
			result.put("userName", userName);
			result.put("password", password);
			result.put("dir", dir);
					
		} catch (Exception e) {
			e.printStackTrace();
			IVRLogger.error("#####SttTransferWebAction message = "+e.getMessage());
			IVRLogger.error(e.getStackTrace());
		} 
		
		return result;
	}
}

