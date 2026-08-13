package cs.com.webaction;

import java.util.StringTokenizer;

import jedix.xwing.action.XwingWebAction;

import com.locus.jedi.transfer.ListParam;
import com.locus.jedi.util.Code;
import com.locus.jedi.util.CodeCache;
import com.locus.jedi.util.CodeUtil;
import com.locus.jedi.util.StringUtils;
import com.locus.jedi.waf.action.WebActionException;
import com.locus.jedi.waf.controller.JediRequest;
import com.locus.jedi.waf.controller.JediResponse;

/**
 * 업무 그룹명 : wfm.com.webaction 서브 업무명 : CodeUtilWebAction.java 작성자 : 공통 작성일 :
 * 2009. 03. 19 설 명 : 코드북 관련 유틸 함
 */
public class CodeUtilWebAction extends XwingWebAction {
	/**
	 * 웹액션 메인 실행 함수 : 캐싱된 코드북 데이타 조회
	 * 
	 * @param req
	 * @param res
	 * @throws WebActionException
	 */
	public void perform(JediRequest req, JediResponse res)
			throws WebActionException {
		try {
			// if(getCsrfFlag()) throw new WebActionException("CSRF", "CSRF::비정상적인 접근입니다.");
			
			String cmd = req.param.getString("cmd");

			if ("getCodes".equals(cmd)) {
				String codeType = req.param.getString("codeType");
				StringTokenizer st = new StringTokenizer(codeType, ",");
				String useyn = req.param.getString("useyn", "");

				String type;
				Code[] code;
				ListParam list;

				while (st.hasMoreTokens()) {
					type = st.nextToken();
					code = CodeUtil.getCodes(type);
					list = new ListParam(
							new String[] { "CODETYPE", "CODEID", "CODENAME",
									"PARENTTYPE", "PARENTID", "ETC1", "ETC2",
									"ETC3", "ETC4", "ETC5", "USEYN", "CENTERCD" });

					for (int i = 0; code != null && i < code.length; i++) {
						if (useyn.length() > 0
								&& !useyn.equalsIgnoreCase(code[i].getUseYn())) {
							continue;
						}

						list.addRow(new Object[] { code[i].getCodeType(),
								code[i].getCodeId(),
								StringUtils.trim(code[i].getCodeName(), ""),
								code[i].getParentType(), code[i].getParentId(),
								code[i].getEtc1(), code[i].getEtc2(),
								code[i].getEtc3(), code[i].getEtc4(),
								code[i].getEtc5(), code[i].getUseYn(),
								code[i].getCenterCd() });
					}

					res.param.addValue(type, list);
				}
			} else if ("getChildCodes".equals(cmd)) {
				String parentType = req.param.getString("parentType");
				String parentId = req.param.getString("parentId");
				String useyn = req.param.getString("useyn", "");

				Code[] code = CodeUtil.getChildCodes(parentType, parentId);
				ListParam list = new ListParam(new String[] { "CODETYPE",
						"CODEID", "CODENAME", "PARENTTYPE", "PARENTID", "ETC1",
						"ETC2", "ETC3", "ETC4", "ETC5", "USEYN", "CENTERCD" });

				for (int i = 0; code != null && i < code.length; i++) {
					if (useyn.length() > 0
							&& !useyn.equalsIgnoreCase(code[i].getUseYn())) {
						continue;
					}

					list.addRow(new Object[] { code[i].getCodeType(),
							code[i].getCodeId(),
							StringUtils.trim(code[i].getCodeName(), ""),
							code[i].getParentType(), code[i].getParentId(),
							code[i].getEtc1(), code[i].getEtc2(),
							code[i].getEtc3(), code[i].getEtc4(),
							code[i].getEtc5(), code[i].getUseYn(),
							code[i].getCenterCd() });
				}

				res.param.addValue(parentType + "_" + parentId, list);
			} else if ("refresh".equals(cmd)) {
				CodeCache.refreshAllCodes();
			}
		} catch (Exception e) {
			throw new WebActionException("CodeUtilWebAction : "
					+ e.getMessage(), e);
		}
	}
};
