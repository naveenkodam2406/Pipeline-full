import Utilities.*

// For using Global params in config page, just use the name directly.
def gitScmSource = [branch:JobDSLSandboxBranch, 
                    url:JobDSLURL, 
                    credential:CredentailHelper.GetcredentialByDescription(this ,GitCredential)]

PipelineHelper.GenTemplateSeedJob(this, jm.readFileInWorkspace("Pipelines/MasterSandboxSeed.json"), gitScmSource)
