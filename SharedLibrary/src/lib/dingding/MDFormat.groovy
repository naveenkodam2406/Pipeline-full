//https://jenkinsci.github.io/dingtalk-plugin/
package lib.dingding

import com.cloudbees.groovy.cps.NonCPS

class MDFormat {
    //https://jenkinsci.github.io/dingtalk-plugin/
        static enum Preformatted{
        FAILURE (""" # <font color=#FF0000 size=5>BUILD FAILURE</font>"""),
        SUCCESS (""" # <font color=#38c221 size=5>BUILD SUCCESS</font>"""),
        ABORTED (""" # <font color=#f7e700 size=5>BUILD ABORTED</font>"""),
        STARTED (""" # <font color=#f7e700 size=5>BUILD STARTED</font>"""),
        UNSTABLE (""" # <font color=#f3f700 size=5>BUILD UNSTABLE</font>"""),
        BOUNDARYLINE ("---"),
        REFSYMBOL ("> "),
        ERROROUTPUT ("##### ERRORS"),
        CHANGESET ("##### CHANGES"),
        BUILDSTAGES  ("##### STAGES"),
        ARTIFACTS  ("##### ARTIFACTS"),
        H_FIVE  ("##### "),
        H_SIX  ("###### "),
        STEP_FAILED (""" <font color=#FF0000>FAILED</font>"""),
        STEP_SUCCESS ("""<font color=#38c221>SUCCESS</font>"""),
        STEP_UNSTABLE ("""<font color=#f3f700>UNSTABLE</font>"""),
        STEP_ABORTED ("""<font color=#f7e700>ABORTED</font>"""),
        STEP_IN_PROGRESS ("""<font color=#f3f700>IN PROGRESS</font>"""),
        STEP_NOT_EXECUTED ("""<font color=#f7e700>NOT_EXECUTED</font>""")
        private final String MDText
        private Preformatted(final String mdText){
            this.MDText = mdText
        }
    }
}