run: export TGTOKEN = $(shell cat .tokens/tgtoken)
run: export PGURL = $(shell cat .tokens/pgurl)
run: export DOMAIN = $(shell cat .tokens/domain)
run: export GHID = $(shell cat .tokens/ghid)
run: export GHSECRET = $(shell cat .tokens/ghsecret)
run:
	clj -A:dev

compile:
	clojure -Srepro -A:native -M:native

ancient:
	clojure -A:dev:ancient -M:ancient

upgrade:
	clojure -A:dev:ancient -M:ancient --upgrade
