package Utilities
class ViewSettings {

    static section(sectionContext, map){ 
        sectionContext.with{
            sections { map.each {k, v -> l:{
                listView {
                    name(k)
                    jobs {
                        regex(v)
                    }
                    width('FULL')
                    alignment('LEFT')
                    //https://github.com/jenkinsci/job-dsl-plugin/blob/master/job-dsl-core/src/main/groovy/javaposse/jobdsl/dsl/views/ColumnsContext.groovy
                    columns{
                        status()
                        weather()
                        name()
                        columnNodes << new Node(null, 'jenkins.branch.DescriptionColumn')
                        lastSuccess()
                        lastFailure()
                        lastDuration()
                        buildButton()
                    }
                }
            }}}
        }    
    }
}
