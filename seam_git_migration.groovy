#!/usr/bin/env groovy

// Go grab Groosh so we can do standard shell commands (http://groovy.codehaus.org/Groosh)
@Grapes([
    @Grab(group='org.codehaus.groovy.modules', module='groosh', version='0.3.6'),
    @GrabConfig(systemClassLoader=true)
])

import groosh.Groosh
Groosh.withGroosh(this)

// Groosh sends out put to an output stream, but XmlSlurper needs an input stream, so using Piped streams and another thread to grab all the modules
def _in = new PipedInputStream()
def out = new PipedOutputStream(_in)

// git repositories seeded from svn
def phase1_dir = new File('test-import-phase1')
// fresh git clones of migrated svn repositories
def phase2_dir = new File('test-import-phase2')

// perhaps commandline arguments?
def phase1 = true
def phase2 = true

Thread.start {
  svn('list', '--xml', 'http://anonsvn.jboss.org/repos/seam/modules/').toStream(out)
}

def modules = new XmlSlurper().parse(_in).list.entry.name.collect { it.text() }
def others = ['parent', 'dist', 'examples']

// testing overrides
//modules = ['faces']
//others = ['parent']

if (phase1) {

   phase1_dir.mkdir()
   cd(phase1_dir)
   
   
   modules.each { m ->
      clone_svn_repo(m, '/modules', m == 'wicket' ? false : true)
   }
   
   others.each { o ->
      clone_svn_repo(o, '/', o == 'parent' ? true : false)
   }
   
   cd('..')

}

if (phase2) {
   phase2_dir.mkdir()
   cd(phase2_dir)
   
   modules.each { m ->
      clone_git_repo("../$phase1_dir", m)
   }
   
   others.each { o ->
      clone_git_repo("../$phase1_dir", o)
   }
   
   update_scm_urls(phase2_dir)
}

def clone_svn_repo(name, context, pull_tags) {
   def svn_uri = "http://anonsvn.jboss.org/repos/seam$context/$name"
   def trunk = 'trunk'
   if (name == 'parent') {
      trunk += '/parent'
   }
   if (pull_tags) {
      git('svn', 'clone', svn_uri, '--no-metadata', '--no-minimize-url', "--trunk=$trunk", '--tags=tags', '--authors-file=../../svn.authors') >> stdout
   }
   else {
      git('svn', 'clone', svn_uri, '--no-metadata', '--no-minimize-url', "--trunk=$trunk", '--authors-file=../../svn.authors') >> stdout
   }
   if (pull_tags) {
      fix_tags(name)
   }
   cd(name)
   //shell.git('reflog', 'expire', '--all', '--expire=now')
   git('gc') >> stdout
   //shell.git('prune')
   //shell.git('fsck', '--full')
   cd('..')
}

def clone_git_repo(root, repo) {
   git('clone', "$root/$repo").waitForExit()
   def github_module = repo
   if (repo == 'remoting') {
      github_module = 'js-remoting'
   }
   else if (repo == 'xml') {
      github_module = 'xml-config'
   }
   cd(repo)
   git('remote', 'add', 'github', "git@github.com:seam/${github_module}.git").waitForExit()
   git('remote', '-v') >> stdout
   //git('push', 'github', 'master')
   cd('..')
}

def fix_tags(repo) {
   cd(repo)
   git('for-each-ref', '--format=%(refname)', 'refs/remotes/tags/*').eachLine { tag_ref ->
      def tag = tag_ref.minus('refs/remotes/tags/')
      def tree = parse_rev("$tag_ref:", false)
      def parent_ref = tag_ref
      while (parse_rev("$parent_ref^:", true) == tree) {
         parent_ref = "$parent_ref^"
      }
      def parent = parse_rev(parent_ref, false)
      def merge = git('merge-base', 'refs/remotes/trunk', parent).text.trim()
      def target_ref
      if (merge == parent) {
         target_ref = parent
         println "$tag references master branch"
      }
      else {
         target_ref = tag_ref
         println "$tag has diverged from the master branch"
      }
      
      println "$tag revision = $target_ref"
      // TODO be sure to unset these before the push
      def env = groosh.getCurrentEnvironment()
      env.put('GIT_COMMITTER_NAME', log_meta(tag_ref, '%an'))
      env.put('GIT_COMMITTER_EMAIL', log_meta(tag_ref, '%ae'))
      env.put('GIT_COMMITTER_DATE', log_meta(tag_ref, '%ai'))
      pipe_meta(tag_ref, '%s') | git('tag', '-a', '-F', '-', tag, target_ref)
      git('update-ref', '-d', tag_ref)
   }
   cd('..')
}

def parse_rev(ref, verify) {
   if (verify) {
      return git('rev-parse', '--quiet', '--verify', ref).text.trim()
   }
   return git('rev-parse', ref).text.trim()
}

def log_meta(tag_ref, symbol) {
   return pipe_meta(tag_ref, symbol).text.trim()
}

def pipe_meta(tag_ref, symbol) {
   return git('log', '-1', "--pretty=\"format:$symbol\"", tag_ref)
}

def modifyFile(file, Closure processText) {
    def text = file.text
    file.write(processText(text))
}

def update_scm_urls(rootDir) {
   rootDir.eachFileRecurse({ f ->
      if (f.file && f.name ==~ /^(readme.txt|pom\.xml$)/) {
         modifyFile(f, { text ->
            text = (text =~ /http:\/\/anonsvn\.jboss\.org\/repos\/(weld|seam\/modules)\/([a-z]+)(\/[a-z]*)*/).replaceAll('git://github.com/seam/$2.git')
            text = (text =~ /https:\/\/svn\.jboss\.org\/repos\/(weld|seam\/modules)\/([a-z]+)(\/[a-z]*)*/).replaceAll('git@github.com:seam/$2.git')
            text = text.replaceAll(/github\.com(:|\/)seam\/remoting/, 'github.com$1seam/js-remoting')
            text = text.replaceAll(/github\.com(:|\/)seam\/xml/, 'github.com$1seam/xml-config')
            text = (text =~ /http:\/\/fisheye\.jboss\.org\/browse\/(([Ss]eam\/)+modules|weld)\/([a-z]+)(\/[a-z]*)*/).replaceAll('http://github.com/seam/$3')
            text = (text =~ /http:\/\/anonsvn\.jboss\.org\/repos\/seam\/([a-z]+\/)*(parent|examples|dist)(\/trunk(\/[a-z\-]*)?)?/).replaceAll('git://github.com/seam/$2.git')
            text = (text =~ /https:\/\/svn\.jboss\.org\/repos\/seam\/([a-z]+\/)*(parent|examples|dist)(\/trunk(\/[a-z\-]*)?)?/).replaceAll('git@github.com:seam/$2.git')
            text = text.replaceAll(/http:\/\/fisheye\.jboss\.org\/browse\/[Ss]eam(\/[a-z\-]*)*/, "http://github.com/seam")
            text = text.replaceAll("http://anonsvn.jboss.org/repos/seam", "http://github.com/seam")
            return text
         })
      }
   })
   // TODO commit modified files
}
