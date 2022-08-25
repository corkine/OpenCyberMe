// ==UserScript==
// @name         CyberMe-zendao
// @namespace    https://cyber.mazhangjing.com/
// @version      0.1
// @description  自动执行禅道每日日报提交
// @author       Corkine Ma
// @match        http://10.110.88.102/pro/effort-batchCreate-*
// @icon         https://www.google.com/s2/favicons?sz=64&domain=88.102
// @grant        GM_xmlhttpRequest
// @connect      *
// ==/UserScript==

const url = "https://cyber.mazhangjing.com/cyber/todo/work-today"
const token = "Basic xxxxxxxx"

function action() {
    //找到第一个非空的最左侧任务框
    let all_inputs = Array.from(document.getElementById("effortBatchAddForm").getElementsByTagName("input"))
    //console.log(all_inputs)
    let task_inputs = all_inputs.filter(item => item.id === "work[]" && !item.value)
    //屏蔽禅道隐藏的输入框
    all_inputs.map(item => {if(item.id === "id[]" || item.id === "actionID[]") {item.hidden=true}})
    //请求数据并填入
    GM_xmlhttpRequest({
        url: url,
        method:'get',
        headers: {
            "Authorization": token
        },
        data:"",
        onerror:function(res){
            console.log(res);
        },
        onload:function(res){
            //console.log(res);
            let obj = JSON.parse(res.responseText);
            console.log(obj.data)
            var i;
            for (i = 0; i < obj.data.length; i++) {
                let now = obj.data[i]
                let title_inp = task_inputs[i]
                let hour_inp = all_inputs[all_inputs.indexOf(title_inp) + 4]
                title_inp.value = now.title
                hour_inp.value = now.hour
            }
        }
    });
}

(function() {
    'use strict';
    let version = "0.0.1"
    console.log("CyberMe zendao plugin v" + version)
    //等待缓慢的加载内容
    setTimeout(action, 1000);
})();