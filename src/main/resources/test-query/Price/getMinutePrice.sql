--TYPE#date,int
--DEFAULT#2019-01-01 11:11:11,100
--OPTIONS#BATCH_PARAMETER,BATCH_RESULT,SELECT,LIMIT
--DESCRIPTION#测试
select *
from minute_history
where trading_time > ${time}
  and id > ${id}