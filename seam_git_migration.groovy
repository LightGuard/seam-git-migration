// Go grab Groosh so we can do standard shell commands (http://groovy.codehaus.org/Groosh)
@Grapes([
    @Grab(group='org.codehaus.groovy.modules', module='groosh', version='[0.3.5,)'),
    @GrabConfig(systemClassLoader=true)
])
import static groosh.Groosh.groosh

def shell = groosh()

def phase1_dir = 'import-phase1'
shell.mkdir(phase1_dir)

def phase2_dir = 'import-phase2'
shell.mkdir(phase2_dir)

shell.cd(phase1_dir)

// Groosh sends out put to an output stream, but XmlSlurper needs an input stream, so using Piped streams and another thread to grab all the modules
def _in = new PipedInputStream()
def out = new PipedOutputStream(_in)

Thread.start {
    shell.svn('list', '--xml', 'http://anonsvn.jboss.org/repos/seam/modules/').toStream(out)
}

def modules = XmlSlurper().parse(_in).list.entry.name.collect { it.text() }
['parent', 'dist'].each { modules << it}

modules.each {

    shell.mkdir('-p', it)
    shell.cd(it)
/*    
    if (it == 'wicket')
        git_clone(false)
    else
        git_clone()
    convert_tags()    
    git_clean()
*/      
    shell.cd("../${phase2_dir}")
/*
    git_clone_push(it) // TODO: add conditions
*/
}

def git_clone(includeTags = true )
{
    def args = ['svn', 'clone', "http://anonsvn.jboss.org/repos/seam/modules/${it}", '--no-metadata', '--no-minimize-url', '--trunk', '--tags', '--authors-file=../../svn.authors']

    if (!includeTags) 
        args - '--tags'
    
    shell.git(args).waitForExit()
}

def git_clean()
{
    shell.git('reflog', 'expire', '--all', '--expire=now')
    shell.git('gc', '--aggressive')
    shell.git('prune')
    shell.git('fsck', '--full')
}

def convert_tags() {
    shell.git('for-each-ref', "--format='%(refname)'", "refs/remotes/tags").eachLine {
        tag = it - 'refs/remots/'
        tree = shell.git('rev-parse', it).text
        
        // TODO: finish fix_tags from old script
    }
} 

def git_clone_push(module, newName = moduleName)
{
    shell.git('clone', "../${phase1_dir}/${module}", newName)
    shell.cd(module)
    shell.git('remote', 'add', 'github', "git@github.com:seam/${newName}.git")
    shell.git('remote', '-v')
    shell.git('push', '--mirror', 'github', 'master')
}

// vim:ts=4:sw=4:sts=4
