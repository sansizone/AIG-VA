package xcron.com.webaction;

import oracle.sql.CLOB;
import jedix.xwing.action.XwingWebAction;

import com.locus.jedi.biz.JediTransaction;
import com.locus.jedi.biz.JediTransactionManager;
import com.locus.jedi.log.ErrorLogger;
import com.locus.jedi.log.IVRLogger;
import com.locus.jedi.service.sql.SQLParam;
import com.locus.jedi.service.sql.SQLServiceManager;
import com.locus.jedi.transfer.ListParam;
import com.locus.jedi.waf.action.WebActionException;
import com.locus.jedi.waf.controller.JediRequest;
import com.locus.jedi.waf.controller.JediResponse;

public class QstMngWebAction extends XwingWebAction {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	

	public void perform(JediRequest req, JediResponse res) throws WebActionException {
		JediTransaction tran = JediTransactionManager.getJediTransaction();
		IVRLogger.debug("QstMngWebAction start");
		String from_date = req.param.getString("from_date");
		String to_date = req.param.getString("to_date");
		String chnl_clcd = req.param.getString("chnl_clcd");
		
		
		try{
			SQLParam sqlParam = new SQLParam();
			
			sqlParam.setSqlName("msens.xcron.hansol.getqstmngwebaction_1"); // 수정일자를 groupby 해서 날짜 정보 가져오기 
			sqlParam.addValue("SDATE", from_date);
			sqlParam.addValue("EDATE", to_date);
			sqlParam.addValue("CHNL_CLCD", chnl_clcd);
			SQLParam sqlResult = SQLServiceManager.getInstance().execute(sqlParam);
			
			if(sqlResult.getCount() > 0){
				tran.begin();
				
				for(int i =0; i< sqlResult.getCount(); i++){
					String altr_dt = sqlResult.getListParam("msens.xcron.hansol.getqstmngwebaction_1").getParam(i).getString("ALTR_DT");
					int cnt = Integer.parseInt(sqlResult.getListParam("msens.xcron.hansol.getqstmngwebaction_1").getParam(i).getString("CNT"));
					
				//	IVRLogger.debug(cnt/500+1 + "  //  " + cnt);
					for(int j =0 ;j< cnt/500+1; j++){

						int from_rnum = 500*j+1;
						int to_rnum = 500*(j+1);
						
						IVRLogger.debug("FROM_RNUM = "+from_rnum + "// TO_RNUM = " + to_rnum);
						
						SQLParam sqlParam1 = new SQLParam();
						sqlParam1.setSqlName("msens.xcron.hansol.getqstmngwebaction_2");
						sqlParam1.addValue("ALTR_DT", altr_dt);
						sqlParam1.addValue("FROM_RNUM", from_rnum);
						sqlParam1.addValue("TO_RNUM", to_rnum);
						sqlParam1.addValue("CHNL_CLCD",chnl_clcd);
						SQLParam sqlResult1 = SQLServiceManager.getInstance().execute(sqlParam1);
						
						ListParam TB_SC_QST_MNG = new ListParam(new String[] {"SC_SNO", "SC_LCLF_CD","SC_SCLF_CD", "QST_CD", "CNTR_CD", "QST_CDNM", "QST_CNTS", "QST_KIND_CD","SORTKEY","DEL_YN", "REG_ID", "REG_DATE", "CHG_ID", "CHG_DATE", "CHNL_CLCD"});
						
						IVRLogger.debug(sqlResult1.getCount());
						
						for(int k =0; k< sqlResult1.getCount(); k++){
							CLOB qst_cnts = (CLOB) sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getValue("QST_CNTS");
							
							String res_qst_cnts = qst_cnts.getSubString(1, (int) qst_cnts.length());
							
							IVRLogger.debug(qst_cnts);
							
							TB_SC_QST_MNG.addRow(new Object[] {
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("SC_SNO"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("SC_LCLF_CD"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("SC_SCLF_CD"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("QST_CD"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("CNTR_CD"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("QST_CDNM"),
									res_qst_cnts,
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("QST_KIND_CD"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("SORTKEY"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("DEL_YN"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("REG_ID"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("REG_DATE"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("CHG_ID"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("CHG_DATE"),
									sqlResult1.getListParam("msens.xcron.hansol.getqstmngwebaction_2").getParam(k).getString("CHNL_CLCD")
							});
						}
						
						IVRLogger.debug("TB_SC_QST_MNG :: SIZE ::" + TB_SC_QST_MNG.rowSize());
						
						if(TB_SC_QST_MNG.rowSize() > 0){
							SQLParam sqlParam2 = new SQLParam();
							sqlParam2.setSqlName("msens.xcron.hansol.getqstmngwebaction_3");
							sqlParam2.addValue("TB_SC_QST_MNG", TB_SC_QST_MNG);
							
							SQLServiceManager.getInstance().execute(sqlParam2, tran);
						}
						
					}
					
					
				}
				
				tran.commit();

			}
			

		}catch (Exception e) {
			tran.rollback();
			e.printStackTrace();
			//ErrorLogger.debug();
			ErrorLogger.debug("####error"+e.getMessage());
			e.printStackTrace();
			IVRLogger.error(e.getMessage());
		}
		
	}
	
	

}

