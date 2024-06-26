(ns knothink.clj.core
  (:gen-class)
  (:use org.httpkit.server)
  (:require [knothink.clj.route :as r]
            [knothink.clj.config :refer [load-config-addition]]
            [knothink.clj.command :as cmd]))



;todo
;* page 의 meta 정보 저장.
;* comment 를 core 에 넣을 것인가.
;* upload 를 특정 command 에 묶을 것인가. upload 경로와 이름 문제.
;* upload 파일을 검색하는 방법.
;* page 이름 변경과 link 문제.


(defn -main [& _]
  (cmd/load-fn)
  (load-config-addition)
  (run-server r/app-handler
              {:port 8888}))



