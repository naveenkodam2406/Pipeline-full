package lib.email

import com.cloudbees.groovy.cps.NonCPS
import groovy.xml.MarkupBuilder

class EmailSender implements Serializable {

    static boolean TO_RESTART_NODE = false
    @NonCPS
    static generateContent(env,currentBuild,ScmEnvList){
        def writer = new StringWriter()
        def builder = new MarkupBuilder(writer)
        builder.html {
            builder.head() {
                CSSGenerator.generateContent(builder)
            }
            builder.body {
                HTMLGenerator.generateReportHeader(builder, env, currentBuild)
                HTMLGenerator.generateBFAReport(builder, env, currentBuild)
                HTMLGenerator.generateStepsReport(builder, currentBuild)
                switch(currentBuild.changeSets.toString()) {
                    case ~/.*SubversionChangeLogSet.*/:
                        HTMLGenerator.generateSvnChangeSetReport(builder, env, currentBuild,ScmEnvList)
                    break;
                    case ~/.*GitChangeSet.*/:
                        HTMLGenerator.generateChangeSetReport(builder, env, currentBuild,ScmEnvList)
                    break;
                }
            }
        }

        return writer.toString()
    }
    @NonCPS
    static generateGitStatus(String gitStatus) {
        def writer = new StringWriter()
        def builder = new MarkupBuilder(writer)
        builder.html {
            builder.head() {
                CSSGenerator.GitStatusReportCSS(builder)
            }
            builder.body {
                GitChangeSummary.generateGitStatusReport(builder, gitStatus)
            }
        }

        return writer.toString()
    }
    @NonCPS
    static generateGitChangeLogM1(String gitChange) {
        def writer = new StringWriter()
        def builder = new MarkupBuilder(writer)
        builder.html {
            builder.head() {
                CSSGenerator.GitStatusReportCSS(builder)
            }
            builder.body {
                builder.PRE(gitChange)
            }
        }

        return writer.toString()
    }
    @NonCPS
    static generateUReportContent(env, currentBuild,manifestVersion=[:]) {
        def writer = new StringWriter()
        def builder = new MarkupBuilder(writer)
        builder.html {
            builder.head() {
                CSSGenerator.UReportCSS(builder)
            }
            builder.body {
                HTMLGenerator.generateUPushReport(builder, env, currentBuild,manifestVersion)
            }
        }

        return writer.toString()
    }
    @NonCPS
    static generateReleaseNotes(script,ScmEnvList=[]) {
        def writer = new StringWriter()
        def builder = new MarkupBuilder(writer)
        builder.html {
            builder.head() {
                CSSGenerator.generateContent(builder)
            }
            builder.body {
                HTMLGenerator.generateChangeSetReport(builder, script.env, script.currentBuild, ScmEnvList)
            }
        }

        return writer.toString()
    }
    @NonCPS
    static generateGitChangeSummary(script,ScmEnvList=[]) {
        def writer = new StringWriter()
        def builder = new MarkupBuilder(writer)
        builder.html {
            builder.head() {
                CSSGenerator.generateContent(builder)
            }
            builder.body {
                GitChangeSummary.generateGitChangeSetReport(builder, script.env, script.currentBuild, ScmEnvList)
            }
        }

        return writer.toString()
    }
    @NonCPS
    static SendEmail(script, recipientEmails="", ScmEnvList=[]) {

        def currentBuild = script.currentBuild
        def env = script.env

        def bfa = new com.sonyericsson.jenkins.plugins.bfa.sod.ScanOnDemandTask(currentBuild.getRawBuild())
        bfa.run()

        def title = "${currentBuild.getFullDisplayName()} - ${currentBuild.currentResult}!"

        def content = generateContent(env, currentBuild,ScmEnvList)

        if(HTMLGenerator.PRIORITIZED_EMAIL_GROUP){
            recipientEmails = HTMLGenerator.PRIORITIZED_EMAIL_GROUP
        }
        if(HTMLGenerator.TO_RESTART_NODE){
            TO_RESTART_NODE = HTMLGenerator.TO_RESTART_NODE
        }
        if (recipientEmails instanceof Collection) {
            recipientEmails = recipientEmails.join(', ')
        }
        script.emailext(body: content,
                        mimeType: 'text/html',
                        recipientProviders: [
						//[$class: 'CulpritsRecipientProvider'],
						//[$class: 'DevelopersRecipientProvider']
						],
                        subject: title,
                        to: recipientEmails)
    }
    @NonCPS
    static SendUReportEmail(script, recipientEmails="",manifestVersion=[:] ) {

        def currentBuild = script.currentBuild
        def env = script.env


        def title = "${env.BuildId} 版本推送"

        def content = generateUReportContent(env, currentBuild,manifestVersion)

        if (recipientEmails instanceof Collection) {
            recipientEmails = recipientEmails.join(', ')
        }
        script.emailext(body: content,
                        mimeType: 'text/html',
                        recipientProviders: [
						//[$class: 'CulpritsRecipientProvider'],
						//[$class: 'DevelopersRecipientProvider']
						],
                        subject: title,
                        to: recipientEmails)
    }

}