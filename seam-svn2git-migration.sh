#!/bin/bash

# Program locations
xmlstarlet=/usr/bin/xmlstarlet
git=/usr/bin/git

phase1_dir=import-phase1
phase2_dir=import-phase2
get_modules=$(svn list --xml http://anonsvn.jboss.org/repos/seam/modules/ | xmlstarlet sel -T -t -m "//name" -c . -n)
get_parent=1
get_examples=1
get_dist=1

fix_tags()
{
   # Convert svn remote tags to lightweight git tags
   $git for-each-ref --format='%(refname)' refs/remotes/tags/* | while read tag_ref; do
      tag=${tag_ref#refs/remotes/tags/}
      tree=$( $git rev-parse "$tag_ref": )

      # find the oldest ancestor for which the tree is the same
      parent_ref="$tag_ref";
      while [ $( $git rev-parse --quiet --verify "$parent_ref"^: ) = "$tree" ]; do
         parent_ref="$parent_ref"^
      done
      parent=$( $git rev-parse "$parent_ref" );

      # if this ancestor is in trunk then we can just tag it
      # otherwise the tag has diverged from trunk and it's actually more like a
      # branch than a tag
      merge=$( $git merge-base "refs/remotes/trunk" $parent );
      if [ "$merge" = "$parent" ]; then
          target_ref=$parent
      else
          echo "tag has diverged: $tag"
          target_ref="$tag_ref"
      fi
      target_ref=$parent

      tag_name=$( $git log -1 --pretty="format:%an" "$tag_ref" )
      tag_email=$( $git log -1 --pretty="format:%ae" "$tag_ref" )
      tag_date=$( $git log -1 --pretty="format:%ai" "$tag_ref" )
      $git log -1 --pretty='format:%s' "$tag_ref" | GIT_COMMITTER_NAME="$tag_name" GIT_COMMITTER_EMAIL="$tag_email" GIT_COMMITTER_DATE="$tag_date" $git tag -a -F - "$tag" "$target_ref"

      $git update-ref -d "$tag_ref"
   done
}

mkdir -p $phase1_dir
cd $phase1_dir

# Partial execution override
#get_modules=()
#get_parent=0
#get_dist=0
#get_examples=0

if [[ $get_parent -eq 1 ]]; then
   # The parent needs to be handled as a special case because its tags are in a non-standard relative location
   mkdir -p parent
   cd parent
   # Don't use -s as we want to drop any branches
   $git svn init http://anonsvn.jboss.org/repos/seam/build --no-metadata --no-minimize-url --trunk=trunk/parent --tags=tags
   $git config svn.authorsfile ../../svn.authors
   $git svn fetch
   fix_tags
   $git gc
   cd ..
fi

if [[ $get_dist -eq 1 ]]; then
   mkdir -p dist
   cd dist
   # Don't use -s as we want to drop any branches
   $git svn init http://anonsvn.jboss.org/repos/seam/dist --no-metadata --no-minimize-url --trunk=trunk
   $git config svn.authorsfile ../../svn.authors
   $git svn fetch
   $git gc
   cd ..
fi

if [[ $get_examples -eq 1 ]]; then
   mkdir -p examples
   cd examples
   # Don't use -s as we want to drop any branches
   $git svn init http://anonsvn.jboss.org/repos/seam/examples --no-metadata --no-minimize-url --trunk=trunk
   $git config svn.authorsfile ../../svn.authors
   $git svn fetch
   $git gc
   cd ..
fi

for module in ${get_modules[@]}; do
  mkdir -p $module
  cd $module
  # Don't use -s as we want to drop any branches
  if [ "$module" = "wicket" ]; then
     # Skip wicket tags (since they are not descendants of the trunk)
     $git svn init http://anonsvn.jboss.org/repos/seam/modules/$module --no-metadata --no-minimize-url --trunk=trunk
  else
     $git svn init http://anonsvn.jboss.org/repos/seam/modules/$module --no-metadata --no-minimize-url --trunk=trunk --tags=tags
  fi
  $git config svn.authorsfile ../../svn.authors
  $git svn fetch
  fix_tags
  $git gc
  cd ..
done

cd ..

mkdir -p $phase2_dir
cd $phase2_dir

if [[ $get_parent -eq 1 ]]; then
   $git clone ../$phase1_dir/parent
   cd parent
   $git remote add github git@github.com:seam/parent.git
   $git remote -v
   cd ..
fi

if [[ $get_dist -eq 1 ]]; then
   $git clone ../$phase1_dir/dist
   cd dist
   $git remote add github git@github.com:seam/dist.git
   $git remote -v
   cd ..
fi

if [[ $get_examples -eq 1 ]]; then
   $git clone ../$phase1_dir/examples
   cd examples
   $git remote add github git@github.com:seam/examples.git
   $git remote -v
   cd ..
fi

for module in ${get_modules[@]}; do
   $git clone ../$phase1_dir/$module
   if [ "$module" = "remoting" ]; then
      github_module="js-remoting"
   elif [ "$module" = "xml" ]; then
      github_module="xml-config"
   else
      github_module="$module"
   fi
   cd $module
   $git remote add github git@github.com:seam/$github_module.git
   $git remote -v
   #$git push github master
   cd ..
done
