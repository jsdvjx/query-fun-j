--TYPE#date,date
--DEFAULT#2019-01-01 11:11:11
--OPTIONS#BATCH_RESULT,SELECT,LIMIT
--DESCRIPTION#测试
select *
from minute_history
where trading_time between ${start} and ${end}