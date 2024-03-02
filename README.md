# knothink?
생각을 끄적이기 위한 웹 프로그램. 생각 조각을 잇기 위해 고민했던 여러 knot-* 프로그램들을 계승하지는 않는다. 
낫띵으로 발음하며 실제로 nothing - 아무것도 아닌것 - 이기도 하다.
주요 기능은 최소화하고 페이지 편집만으로 기능을 확장할 수 있는 것이 특징이라면 특징이다. 라고 하기엔 아직 개발 초기..

# extension
확장 이름이 abc 라면, @fn-abc.clj 로 확장 파일을 구현 한다.  
```clojure
;; @fn-abc.clj
(defn fn-abc [[param1 param2]]
  (str param1 param2))
```
문서 내에 ``@abc hi knothink@`` 라고 호출 하였다면 해당 함수의 결과인 ``hi knothink`` 로 대치 된다. 
파라메터는 공백으로 구분하는데 만약 파라메터가 공백을 포함한다면 따옴표로 감싸주어야 한다.
``ex) @abc hi "knot think"@ => hi knot think`` 


