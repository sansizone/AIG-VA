#-*- encoding:utf-8 -*-

import pymssql
from collections import defaultdict
from utils import log, utils
import datetime

class mssqldb(object):
    def __init__(self, _dbname="msens_ob", _host = "localhost", _host_back = "localhost", _port = 1433, _user = "bXNlbnM=", _pwd = "bXNlbnMh"):
        self._dbname=_dbname
        self._host=_host
        self._host_back=_host_back
        self._port=_port
        self._user=_user
        self._pwd=_pwd

        self.create_connection()

        self.utils = utils()

    def __del__(self):
        #self.db.close()
        del self.utils


    def create_connection(self):
        try:
            self.db = pymssql.connect(server=self._host, user=self._user, password=self._pwd, database=self._dbname)
            print("[[ds_proc]] DB INFO :: %s:%s  %s  " % (self._host, self._port, self._dbname))
        except pymssql.DatabaseError, err:
            #print("Connectionn Failed, Trnsfer to Backup DB")
            try:
                self.db = pymssql.connect(server=self._host_back, user=self._user, password=self._pwd, database=self._dbname)
                print("[[ds_proc]] DB INFO :: %s:%s  %s  " % (self._host_back, self._port, self._dbname))
            except pymssql.DatabaseError, err:
                print("[[dataproc]] DB Connectionn Failed, Check DB Server!")
                raise err
                pass

    """
        Read STT contents
    """
    def read_contents(self):
        self.create_connection()
        #db = pymssql.connect(server=self._host, user=self._user, password=self._pwd, database=self._dbname)
        with self.db.cursor() as cursor: #
            cursor.execute("SELECT top(50) A.UCID, B.CON_ENT_DGN_NO, A.CONTENT, A.RXTX_GB \
                              FROM ms_stt_rslt_merge A, ms_stt_meta B \
                             WHERE A.UCID=B.UCID AND B.STT_FLAG='Y' AND B.TA_FLAG='N' AND B.BATCH_YN='N' AND A.ENC_FLAG='N' \
                             ORDER BY B.CON_ENT_DGN_NO ")
                             # AND B.REG_DATE BETWEEN format(dateadd(d,-1,getdate()),'yyyyMMdd')+'000000' and format(dateadd(d,0,getdate()),'yyyyMMdd')+'235959' ")
                             # ORDER BY B.REG_DATE ")
            resultset = cursor.fetchall()
            return resultset   # tuple that contains tuples (( , ), ( , ), ....)
        self.db.close()


    """
        Read Script info + META
    """
    def read_script_map(self, _textid, _con_ent_dgn_no): #AND TA_FLAG='N'
        #print(_textid, _con_ent_dgn_no)
        self.create_connection()
        #self.db = pymssql.connect(server=self._host, user=self._user, password=self._pwd, database=self._dbname)
        with self.db.cursor() as cursor:
            sql = "SELECT A.SC_SNO, A.SC_LCLF_CD, A.SC_SCLF_CD, A.QST_CD, A.CNTR_CD, S.CTRA_INSA_CLCD, A.SCRIPT_SENT_ID, A.QST_RATE, A.QST_KEYWORD, A.SCRIPT_TA_SENT  \
                             FROM ms_script_sent A, \
                           		 (SELECT X.UCID, X.CON_ENT_DGN_NO, X.PROD_CD, X.DMBO_CD, CS.Items AS CTRA_INSA_CLCD, X.SC_LCLF_CD, X.CENTERCD, X.G_AGE, X.P_AGE, X.CON_CON_NA_Y_CNT, X.P_INSUR_AGE,  \
                   					X.APT_CHK_TYPE, X.HOME_CHK_TYPE, X.FNNCL_SPEC, X.FNNCL_SPEC_YN  \
                   					FROM (SELECT A.UCID, A.CON_ENT_DGN_NO, A.PROD_CD,A.DMBO_CD,A.CTRA_INSA_CLCD,A.SC_LCLF_CD,A.CENTERCD,A.G_AGE,A.P_AGE,A.CON_CON_NA_Y_CNT,A.P_INSUR_AGE,  \
                			        	    A.APT_CHK_TYPE, A.HOME_CHK_TYPE, A.FNNCL_SPEC, A.FNNCL_SPEC_YN \
                			        	    FROM ms_stt_meta A  \
                			         	   WHERE A.STT_FLAG='Y'  \
                			         		 AND A.UCID = %s AND A.CON_ENT_DGN_NO = %s ) X cross apply (SELECT Items FROM fn_split_string(X.CTRA_INSA_CLCD,',')) CS \
                			       ) S \
                            WHERE A.EXCEPT_YN <> 'Y'  \
							  AND A.DEL_YN <> 'Y' \
                              AND A.SC_SNO IN (SELECT SC_SNO FROM TB_SC_MNG WHERE USE_YN<>'N' AND PROD_CD = S.PROD_CD AND USE_YN='Y'   \
                                                                              AND CTRA_INSA_CLCD IN (SELECT Items FROM fn_split_string(S.CTRA_INSA_CLCD,','))   \
                                                                              AND SC_LCLF_CD IN (SELECT Items FROM fn_split_string(S.SC_LCLF_CD,',')))  \
                              AND A.CNTR_CD = (CASE WHEN (SELECT COUNT(SC_SNO)  \
                                                            FROM ms_script_mng  \
                                                           WHERE SC_SNO IN (SELECT SC_SNO  FROM TB_SC_MNG WHERE USE_YN<>'N' AND PROD_CD = S.PROD_CD AND USE_YN='Y'    \
                                                                                                            AND CTRA_INSA_CLCD IN (SELECT Items FROM fn_split_string(S.CTRA_INSA_CLCD,','))    \
                                                                                                            AND SC_LCLF_CD  IN (SELECT Items FROM fn_split_string(S.SC_LCLF_CD,',')))    \
                                                                                                            AND CNTR_CD = S.CENTERCD) = 0 THEN '00000' ELSE S.CENTERCD END )   \
                              AND (A.QST_KIND_CD = 'A' OR A.QST_KIND_CD ='E'  \
                               OR ( QST_CD IN (SELECT DISTINCT QST_CD FROM TB_QST_KIND_MNG WHERE QST_KIND_CD ='B' AND CNTR_CD = A.CNTR_CD AND QST_COND IN (SELECT Items FROM fn_split_string(S.DMBO_CD,',')) ) )  \
                               OR ( QST_CD IN (SELECT DISTINCT QST_CD FROM TB_QST_KIND_MNG WHERE QST_KIND_CD ='C' AND CNTR_CD = A.CNTR_CD AND QST_COND = S.P_INSUR_AGE) )     \
                               OR ( QST_CD IN (SELECT DISTINCT QST_CD FROM TB_QST_KIND_MNG WHERE QST_KIND_CD ='D' AND CNTR_CD = A.CNTR_CD AND QST_COND = S.CON_CON_NA_Y_CNT) )     \
                               OR ( QST_CD IN (SELECT DISTINCT QST_CD FROM TB_QST_KIND_MNG WHERE QST_KIND_CD ='F' AND CNTR_CD = A.CNTR_CD AND QST_COND = S.G_AGE) )  \
                               OR ( QST_CD IN (SELECT DISTINCT QST_CD FROM TB_QST_KIND_MNG WHERE QST_KIND_CD ='G' AND CNTR_CD = A.CNTR_CD AND QST_COND = S.G_AGE) )  \
                               OR ( QST_CD IN (SELECT DISTINCT QST_CD FROM TB_QST_KIND_MNG WHERE QST_KIND_CD ='H' AND CNTR_CD = A.CNTR_CD AND QST_COND = S.APT_CHK_TYPE) )  \
                               OR ( QST_CD IN (SELECT DISTINCT QST_CD FROM TB_QST_KIND_MNG WHERE QST_KIND_CD ='I' AND CNTR_CD = A.CNTR_CD AND QST_COND = S.HOME_CHK_TYPE) )  \
                               OR ( QST_CD IN (SELECT DISTINCT QST_CD FROM TB_QST_KIND_MNG WHERE QST_KIND_CD =S.FNNCL_SPEC AND CNTR_CD = A.CNTR_CD AND QST_COND = S.FNNCL_SPEC_YN) ) )" 
            cursor.execute(sql, (_textid, _con_ent_dgn_no) )

            resultset = cursor.fetchall()

            return resultset   # list that contains tuples (( , ), ( , ), ....)
        self.db.close()


    """
        Register sentence_similarity
    """
    def register_sent_similarity(self, _textid, _result_set):
        self.create_connection()
        #db = pymssql.connect(server=self._host, user=self._user, password=self._pwd, database=self._dbname, charset='utf8')
        try:
            with self.db.cursor() as cursor:
                sql = "IF NOT EXISTS \
    								(SELECT * \
    								   FROM ms_regu_score \
    							      WHERE CON_ENT_DGN_NO=%s AND UCID=%s AND QST_CD=%s AND SCRIPT_SENT_ID=%s AND STT_SENT_ID=%s) \
    				  INSERT INTO  ms_regu_score (CON_ENT_DGN_NO, UCID, SC_SNO, SC_LCLF_CD, SC_SCLF_CD, QST_CD, CNTR_CD, CTRA_INSA_CLCD,  \
                                                  SCRIPT_SENT_ID, STT_SENT_ID, START_TIME, TA_SCORE)  \
                      VALUES (%s,%s,%s,%s,%s,%s,%s,%s,\
                              %s,%s,%s,%s);"
                for r_set in _result_set:
                    for c_set in r_set[1]:
                        #r_set[0],
                        cursor.execute( sql, (c_set[0], _textid, c_set[5], c_set[8], c_set[9], \
                                              c_set[0], _textid, c_set[2], c_set[3], c_set[4], c_set[5], c_set[6], c_set[7], \
                                              c_set[8], c_set[9], c_set[10], str(round(c_set[11]*100)) ) )
                        self.db.commit()
        except Exception, e:
            raise e
            self.db.rollback()
        finally :
            self.db.close()


    """
        Update ta flag (N, S, T, Y)
    """
    def update_tag_flag(self, _textid, _conid, _flag):
        self.create_connection()
        #db = pymssql.connect(server=self._host, user=self._user, password=self._pwd, database=self._dbname)
        now=datetime.datetime.now()
        try:
            with self.db.cursor() as cursor:
                sql = "UPDATE ms_stt_meta SET TA_FLAG = %s, TA_R_TIME = %s WHERE UCID = %s AND CON_ENT_DGN_NO = %s;"
                cursor.execute( sql, (_flag, now.strftime('%Y%m%d%H%M%S'), _textid, _conid) )
                self.db.commit()
        except Exception, e:
            raise e
            self.db.rollback()
        finally :
            self.db.close()


    """
        Check ta flag (S)
    """
    def check_flag(self) :
        self.create_connection()
        with self.db.cursor() as cursor: #
            cursor.execute("SELECT UCID, CON_ENT_DGN_NO \
                              FROM ms_stt_meta  \
                             WHERE TA_FLAG='S' \
                               AND DATEDIFF(minute, substring(ta_r_time,1,4)+'-'+substring(ta_r_time,5,2)+'-'+substring(ta_r_time,7,2)+' '+substring(ta_r_time,9,2)+':'+substring(ta_r_time,11,2)+':'+substring(ta_r_time,13,2), GETDATE()) >= 60 ")
            resultset = cursor.fetchall()
            return resultset   # tuple that contains tuples (( , ), ( , ), ....)
        self.db.close()





    """
        parameters :
            _textid : rec_key
            _result_set : list[[rxtx string, keywords list] ...]
                ex) [ ["full", ["keyword", ...] ],
                      ["rx", ["keyword", ...] ],
                      ["tx", ["keyword", ...] ] ]
    """
    def register_keyword_extraction(self, _textid, _result_set):
        update_keywords = "#".join(r_set[0].split('/')[0] for r_set in _result_set)
        db = pymssql.connect(server=self._host, user=self._user, password=self._pwd, database=self._dbname)
        with db.cursor() as cursor:
            sql = "INSERT INTO vs_keyword VALUES (%s,%s,%s);"
            cursor.execute( sql, (_textid, "F",update_keywords) )
            db.commit()
        db.close()


class extractFile(object):
    def __init__(self):
        self.utils = utils()

    def __del__(self):
        del self.utils

    """
        Extract sentence_similarity
    """
    def extractFile_sent_similarity(self, _textid, _result_set, _path="./result/"):
        _currentTime = self.utils.get_Now()

        try:
            self.file = open(_path+_textid+"_"+_currentTime+".txt", "w")
        except IOError as e:
            print("error", e)
            self.file.close()

        for r_set in _result_set:
            _row = "STT>>> "+ r_set[0]+"\n"
            for r in r_set[1]:
                _row += "SCRIPT>>> "+ r[4]+"\n"
                _row += "SCORE>>> "+ str(r[3])+"\n"
                self.file.write(_row)

        self.file.close()

    """
        Extract mining_result
    """
    def extractFile_mining_result(self, _textid, _result_set, _path="./result/"):
        _currentTime = self.utils.get_Now()

        try:
            self.file = open(_path+_textid+_currentTime+"_key_coll.txt", "w")
        except IOError as e:
            print("error", e)
            self.file.close()

        for r_set in _result_set:
            _row = ">>> KEYWORD >>> "+"\n"
            for k in r_set[1]:
                _row += "{word:" + k[0] + "    weight:" + str(k[1]) +"} " +"\n"

            if isinstance(r_set[2], defaultdict) == False:
                #raise TypeError('_collocations type is not collections.defaultdict')
                pass
            else:
                _row += "\n" + ">>> COLLOCATION >>> "+"\n"
                for t in r_set[2].items():
                    if len(t) < 2:
                        raise ValueError('collocation words count is less then 2')
                    else:
                        if isinstance(t[0], unicode) == False:
                            raise TypeError('collocation dictionary key is not unicode string')
                        if isinstance(t[1], list) == False:
                            raise TypeError('collocation dictionary value is not list type')

                    #w_list = [tm for tm in t[1]]
                    _row += "{ref_word:" + t[0] + "    col_words:"
                    for tm in t[1]:
                        _row += tm +","
                    _row += "} " +"\n"

        try:
            self.file.write(_row)
        except Exception, e:
            raise e

        self.file.close()


    """
        Extract classification(negative sentences)
    """
    def extractFile_clf(self, _textid, _result_set, _pos, _path="./result/"):
        _currentTime = self.utils.get_Now()

        try:
            self.file = open(_path+_textid+_currentTime+"_clf.txt", "w")
        except IOError as e:
            print("error", e)
            self.file.close()


        _row = ""
        for idx, r_set in enumerate(_result_set):

            if len(r_set[1]) > 0:
                _row += r_set[1]['name']+">>> " + str(r_set[2][r_set[1]['prob_idx']]) + ">>> " + r_set[0]  + "\n"
                #update_result.append({"sentence":r_set[0], "predicted":r_set[1], "position":p_set, "probability":r_set[2]})

        try:
            self.file.write(_row)
        except Exception, e:
            raise e

        self.file.close()


if __name__=="__main__":
    print("start")
    #db = mssqldb(_host="10.1.12.62", _port=1443, _dbname="msens_ob", _user="msens", _pwd="msens")
    #db.read_contents()
