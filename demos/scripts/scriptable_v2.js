const user = "xxxxx"
const pass = "xxxxx"
const place = "na-tie"
const host2 = "https://cyber.mazhangjing.com"

function closeAfterWork() {
    let t = new Date()
    t.setHours(17, 30)
    let sec = t.getTime() - new Date().getTime()
    return sec < 1000 * 60 * 50
}

function closeAfterWorkOvertime() {
    let t = new Date()
    t.setHours(20, 0)
    let sec = t.getTime() - new Date().getTime()
    return sec < 1000 * 60 * 50
}

async function HostData(path) {
    let url = host2 + path
    let req = new Request(url)
    return await req.loadJSON()
}

function genMark(overtime, hint) {
    let mark = "⚪️ "
    if (overtime.Planned && !hint.OffWork) {
        if (overtime.Ordered && overtime.Checked && hint.OffWork) {
            mark = "🟢 "
        } else if (overtime.Ordered && overtime.Checked) {
            mark = "🟡 "
        } else if (overtime.Ordered) {
            mark = "🟠 "
        } else {
            mark = "🔴 "
        }
        if (hint.NeedMorningCheck) {
            mark = "🔴 "
        }
    } else {
        if (hint.OffWork) {
            mark = "🟢 "
        } else {
            mark = "🟡 "
        }
        if (hint.NeedMorningCheck) {
            mark = "🔴 "
        }
    }
    return mark;
}

function genTODONumber(starCount) {
    surl = "";
    if (starCount < 10 && starCount != 0) {
        surl = "https://cyber.mazhangjing.com/scriptable/x" + starCount + ".png"
    }
    if (starCount >= 10) {
        surl = "https://cyber.mazhangjing.com/scriptable/xN.png"
    }
    return surl;
}

function genMainWold(today, hint, overtime, todayCutFitDone) {
    let mainWord = ''
    let backgroundColor = Color.blue()
    let n = new Date().getHours()
    if (n >= 0 && n < 9) { //早上显示
        backgroundColor = new Color("99cc66", 1.0)
        if (today.starCount == 0) {
            mainWord = "🗓   计划"
        } else {
            mainWord = "📆   规划"
        }
        if (hint.NeedMorningCheck) {
            backgroundColor = new Color("cc0033", 1.0)
            mainWord = "🏬   签到"
        }
    } else if (!hint.OffWork || (overtime.Planned && !overtime.Checked)) { //下午下班前
        backgroundColor = new Color("0089cc", 1.0)
        mainWord = "🤹   积累"
        if (!overtime.Planned && closeAfterWork()) {
            backgroundColor = new Color("cc0033", 1.0)
            mainWord = '🔖   打卡'
        } else if (hint.NeedWork && hint.OffWork) {
            if (!todayCutFitDone) {
                backgroundColor = new Color("cc4455", 1.0)
                mainWord = "🎾   活动"
            } else {
                backgroundColor = new Color("009966", 1.0)
                mainWord = "🎉   晚安"
            }
        } else if (overtime.Planned && closeAfterWorkOvertime()) {
            //第一种情况：没签退，没 ordered //提醒
            //没签退，ordered，但没 checked //提醒
            //没签退，ordered 且 checked //提醒
            //签退，ordered 且 checked //不提醒
            let notNeedAlarm = (hint.OffWork) && overtime.Ordered && overtime.Checked
            if (!notNeedAlarm) {
                backgroundColor = new Color("cc0033", 1.0)
                mainWord = '🗂   签退'
            }
        }
    } else { //其余时间
        if (!todayCutFitDone) {
            backgroundColor = new Color("cc4455", 1.0)
            mainWord = "🎾   活动"
        } else {
            backgroundColor = new Color("009966", 1.0)
            mainWord = "🎉   晚安"
        }
    }
    return {
        word: mainWord,
        bgColor: backgroundColor
    }
}

async function call() {
    let hint = await HostData(`/cyber/check/hint_summary_todo?secret=${pass}&user=${user}&id=${place}`)
    console.log(hint)
    let summary = hint.Summary
    let today = hint.Todo
    let overtime = {
        Ordered: hint.Overtime.Ordered,
        Checked: hint.Overtime.Checked,
        Planned: hint.Overtime.Planned
    }

    let weather = (hint.Weather || {"weather": ""}).weather
    let todayCalories = hint.FitnessEnergy.Fitness.TodayCalories
    let todayEnergyCutOld = (hint.FitnessEnergy.TodayCutCalories).toFixed(0)
    let todayEnergyCut = (hint.FitnessEnergy.Fitness.TodayCalories).toFixed(0) //MaxInTakeCalories
    let todayCutDone = hint.FitnessEnergy.AchievedCutGoal
    let todayFitDone = hint.FitnessEnergy.Fitness.IsOK//
    let todayCutFitDone = hint.FitnessEnergy.Fitness.TodayCalories > 550
    let fitHint = hint.FitnessEnergy.Fitness.FitnessHint

    let cleanHint = hint.Clean.HabitHint

    let todayBreathMinutes = hint.Breath.TodayBreathMinutes
    let breathDayCount = hint.Breath.DayCountNow
    let breathHint = breathDayCount + "/" + todayBreathMinutes

    let noblueDayCount = hint.Blue.MaxNoBlueDay
    let blueMonthCount = hint.Blue.MonthBlueCount

    // console.log(hint)

    //生成加班指示灯图标: 红色 - 未打上班卡或加班预定但未勾选，橙色 - 未打加班晚确认卡，黄色 - 等待打下班卡，绿色 - 下班
    let mark = genMark(overtime, hint);

    let mainWordObj = genMainWold(today, hint, overtime, todayCutFitDone);

    let todoBackgroundURL = genTODONumber(today.starCount);
    //let todoBackgroundURL = ""

    let smallTextSize = 8
    let mainWordTextSize = 45

    const widget = new ListWidget()
    widget.backgroundColor = mainWordObj.bgColor
    if (todoBackgroundURL !== "") {
        let req = new Request(todoBackgroundURL)
        let image = await req.loadImage()
        widget.backgroundImage = image
    }

    //生成第一行信息：日期、BLUE、卡路里
    let date = new Date()
    let dateLine = (date.getMonth() + 1) + "月" + date.getDate() + "日"
    let blueLine = " 🕳" + noblueDayCount
    let energyLine = !todayCutDone ? "🚨" + todayEnergyCut : " 🚧" + todayEnergyCut

    var workLine = ""
    if (hint.NeedWork) {
        workLine = mark + hint.WorkHour + "/" + summary.OvertimeInfoV2.OverTimeAlsoNeed + "h" + " "
    } else {
        workLine = mark + hint.WorkHour + "/" +
            summary.OvertimeInfoV2.OverTimeAlsoNeed + "h" + " "
    }

    let firstLine = workLine + " 🎀 " + cleanHint + blueLine;
    let firstLineWidget = widget.addText(firstLine);
    firstLineWidget.textColor = Color.white();
    firstLineWidget.font = new Font('Verdana', smallTextSize);
    widget.addSpacer(2)

    //生成第二行信息：清洁、健身和呼吸
    let secondLine = weather
    let secondLineWidget = widget.addText(secondLine)
    secondLineWidget.textColor = Color.white()
    secondLineWidget.font = new Font('Verdana', smallTextSize)
    widget.addSpacer(0)

    let mainWordWidget = widget.addText(mainWordObj.word)
    mainWordWidget.font = new Font('Verdana', mainWordTextSize)
    mainWordWidget.textColor = Color.white()

    Script.setWidget(widget)
    widget.addSpacer(0)
    Script.complete()
    widget.presentSmall()
}

try {
    await call()
} catch (e) {
    let f = FileManager.iCloud()
    let d = f.documentsDirectory()
    let f2 = f.joinPath(d, "result.log")
    f.writeString(f2, String(new Date()) + String(e))
    throw e
}