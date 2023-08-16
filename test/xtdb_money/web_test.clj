(ns xtdb-money.web-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as x]
            [ring.mock.request :as req]
            [dgknght.app-lib.test-assertions]
            [dgknght.app-lib.test :refer [parse-html-body]]
            [xtdb-money.handler :refer [app]]))

(deftest fetch-the-home-page
  (let [{:keys [html-body] :as res} (-> (req/request :get "/")
                                        app
                                        parse-html-body)
        zipped (xml-zip html-body)]
    (is (http-success? res))
    (is (= "MultiMoney Double-Entry Accounting"
           (x/xml1-> zipped :html :head :title x/text))
        "The HTML content as the correct title")
    (is (some #(x/xml1-> % :div (x/attr= :id "app"))
              (dz/descendants (x/xml1-> zipped :html :body))))
    (is (some #(x/xml1-> % :h1 (x/text= "Welcome!"))
              (dz/descendants (x/xml1-> zipped :html :body)))
        "The HTML body has the correct page header (during JavaScript startup)")))

(deftest fetch-the-stylesheet
  (let [res (app (req/request :get "/assets/css/site.css"))]
    (is (http-success? res))))
