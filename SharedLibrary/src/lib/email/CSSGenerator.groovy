package lib.email

import com.cloudbees.groovy.cps.NonCPS

class CSSGenerator {

    @NonCPS
    static void generateContent(builder) {
        builder.style(type: "text/css") {
            mkp.yieldUnescaped('''
            BODY {
            font-family:Calibri,Verdana,Helvetica;
            font-size:11px;
            }
            TABLE{
                width:100%;
            }
            pre.buildlog{
                font-family:Calibri,Verdana,Helvetica;
                font-size:11px;
            }
            pre.failureLine { 
                font-family:Calibri,Verdana,Helvetica;
                font-size:11px;
                background-color: red; color: white;
            }
            b a { color: black;}
            h1, h2, h3{ color:black; }
            span {margin:0px,5px,0px,0px;}
            TR.build_step:nth-child(even) { background-color:#EDEDED; }
            TD.build_success { color: white; background-color: green; font-size: 200%; }
            TD.build_failure { color: white; background-color: red; font-size: 200%; }
            TD.build_aborted { color: white; background-color: orange; font-size: 200%; }
            TD.shelved_change_header { color:white; background-color: #ffb259; font-size:130% }
            TD.shelved_change_revision { color:black; background-color:#ffe4a6; font-size:110% }
            TD.changes_header { color:white; background-color: #69C3FF; font-size:130% }
            TD.changes_server { color:white; background-color: #045905; font-size:130% }
            TD.changes_since_previous { color:black; background-color:#A1DBFF; font-size:110% }
            TD.changes_since_success { color:black; background-color:LightGray; font-size:110% }
            TD.build_steps_header { color:white; background-color:#44A8EB; font-size:130% }
            TD.error_header { color:white; background-color:#000000; font-size:130% }
            TD.stage_FAILED { color:red; }
            TD.stage_SUCCESS { color:green; }
            TD.stage_ABORTED { color:orange; }
            TD.stage_NOT_EXECUTED { color:orange; }
            TD.console { font-family:Courier New; }
            TD.failureName {color: red; font-size=110%}
            SUMMARY {
				font-family:Calibri,Verdana,Helvetica;
			}
            SUMMARY SH {
                font-size:30px;
				font-weight: bold;
				margin-left:5px;
            }
			SUMMARY SSH {
                font-size:25px;
				font-weight: bold;
				margin-left:25px;
            }
			SUMMARY OL {
                font-size:16px;
				list-style:decimal;
				list-style-position: outside;
			}
        ''')
        }
    }
    @NonCPS
    static void UReportCSS(builder) {
        builder.style(type: "text/css") {
            mkp.yieldUnescaped('''
            TABLE 	{background-color:gray;border-collapse: collapse; }
            TH 			    { background-color: #37caff; border: 1px solid black; padding: 1px; }
            TD 			    { height:25px; border: 1px solid black; padding: 1px; }
            TH.left   	    { width:200px; }
            TH.right  	    { width:300px; }
            TR.even   	    {background-color: #eff0f7; }
            TR.odd   	    {background-color: #fcfcfc; }
        ''')
        }
    }
    @NonCPS
    static void GitStatusReportCSS(builder) {
        builder.style(type: "text/css") {
            mkp.yieldUnescaped('''
            BODY {
                font-family:Calibri,Verdana,Helvetica;
                font-size:11px;
            }
            PRE WARNING {
                background-color: orange; color: white;
            }
            PRE ERROR {
                background-color: red; color: white;
            }
        ''')
        }
    }
}