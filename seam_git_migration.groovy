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

new XmlSlurper().parse(_in).list.entry.name.collect { it.text() }.each {

    shell.mkdir('-p', it)
    shell.cd(it)
/*    
    if (it == 'wicket')
        shell.git('svn', 'clone', "http://anonsvn.jboss.org/repos/seam/modules/${it}", '--no-metadata', '--no-minimize-url', '--trunk', '--tags', '--authors-file=../../svn.authors').waitForExit()
    else
        shell.git('svn', 'clone', "http://anonsvn.jboss.org/repos/seam/modules/${it}", '--no-metadata', '--no-minimize-url', '--trunk', '--authors-file=../../svn.authors').waitForExit()
*/    
    fix_tags()    
/*
    shell.git('reflog', 'expire', '--all', '--expire=now')
    shell.git('gc', '--aggressive')
    shell.git('prune')
    shell.git('fsck', '--full')
*/      
    shell.cd('..')
}

def fix_tags() {

} 