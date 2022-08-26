// ==UserScript==
// @name         INSPUR 加班点心数据库记录
// @namespace    mvst
// @version      0.1
// @description  try to take over the world!
// @author       You
// @match        http://10.69.67.8:8080
// @grant        GM_xmlhttpRequest
// ==/UserScript==

(function() {
    'use strict';
    function upload(word) {
        let req = new XMLHttpRequest();
        req.open("GET","http://ct.mazhangjing.com:8999/check/overtime_order?secret=SECRETHERE&user=USERNAMEHERE&order=" + word);
        req.onreadystatechange = function() {
            if (req.readyState === XMLHttpRequest.DONE && req.status === 200) {
                let result = req.responseText;
                let data = JSON.parse(result);
                console.log(data.message);
                window.alert("上传数据中... \n" + data.message);
            }
        }
        req.send();
    }
    function setDone() {
        upload("true");
    }
    function cancel() {
        upload("false");
    }
    //cancel();

    window.onload = function() {
        //let target = document.querySelector("#app > div > div.main-container > section > div > div:nth-child(5) > label > span.el-checkbox__input > input");
        let target = document.getElementsByTagName("input")[0];
        console.log(target);
        //document.querySelector("#app > div > div.main-container > section > div > div:nth-child(5)")
        document.getElementsByClassName("el-switch__core")[0].addEventListener("click", (e) => {
                //target.onselect = function(e) {
                if (!target.checked) {
                    setDone();
                } else {
                    cancel();
                }
            }
            ,true );}
})();