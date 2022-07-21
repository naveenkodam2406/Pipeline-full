package lib.dingding

import com.cloudbees.groovy.cps.NonCPS

class DingSender implements Serializable {
    @NonCPS
    static SendDingMSG(script, robotId, status="") {
        def bfa = new com.sonyericsson.jenkins.plugins.bfa.sod.ScanOnDemandTask(script.currentBuild.getRawBuild())
        bfa.run()
        if(robotId == null || robotId ==""){
            script.print "No RobotId for DingTalk, skipping sending message"
            return
        }
        if(status == "" || status == null) status = script.currentBuild.currentResult
        def mdBody = DingMSGGenerator.GenerateSnipMD(script.env, script.currentBuild,status)
        script.print = mdBody
        script.dingtalk (
            robot: robotId,
            type: "MARKDOWN",
            at: [],
            atAll: false,
            title: "BUILD ${status}",
            text: mdBody,
            messageUrl: script.env.RUN_DISPLAY_URL,
            picUrl: '',
            singleTitle: "BUILD ${status}",
            btns: [],
            btnLayout: 'H',
            hideAvatar: false
        )
    }
}