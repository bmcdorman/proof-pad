#How to build

1. Download the Proof Pad source from one of the methods provided above.
2. Download and compile [`RSyntaxTextArea`](http://sourceforge.net/projects/rsyntaxtextarea/?_test=b). Export it to a .jar and add it to the build path for Proof Pad.
3. Download a jar for [`Xstream`](http://xstream.codehaus.org/download.html) and add it to the build path.
4. If you're not on OS X, download [Orange Extensions](http://ymasory.github.com/OrangeExtensions/) and add that to the build path. (Orange stubs out the OS X specific function calls so that the OS X specific code doesn't have to be removed to compile on Windows/Linux. You could, alternatively, comment out the large block of code in Main.java surrounded by `if (isMac)`.)
5. Download [ACL2](http://acl2s.ccs.neu.edu/acl2s/update/images/) for your platform and unzip it somewhere.
6. Edit the path to ACL2 in `GenerateCache.java` to point to your local installation of ACL2. Compile and run that file. It will produce `cache.dat` in the main project directory.
7. Copy/move/symlink the dracula/ directory from the acl2 directory
8. Compile and run `org.proofpad.Main`.

If you encounter any problems with these build instructions, email me at calebegg@gmail.com.
