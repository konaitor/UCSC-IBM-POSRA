//Tests batches of diagrams through OSRA 
#include <iostream>
#include <fstream>
#include <stdio.h>
#include <vector>
#include <unistd.h>
using namespace std;

//Flags:
//-b : basic (no parens)
//-p : parenthesis + brackets
//-h : horizontal parenthesis + brackets
//-n : nested parenthesis
//-a : ALL

void test_diagrams (string, string);

int main(int argc, char **argv){
      int c;
      //cases for each flag
      while ((c = getopt(argc, argv, "bphna")) != -1){
            switch (c) {
                  case 'b':
                        test_diagrams("./test/basic/list.txt", "basic/");
                        break;
                  case 'p':
                        test_diagrams("./test/parenthesis/list.txt", "parenthesis/");
                        break;
                  case 'h':
                        test_diagrams("./test/horizontal/list.txt", "horizontal/");
                        break;
                  case 'n':
                        test_diagrams("./test/nested/list.txt", "nested/");
                        break;
                  case 'a':
                        test_diagrams("./test/basic/list.txt", "basic/");
                        test_diagrams("./test/parenthesis/list.txt", "parenthesis/");
                        test_diagrams("./test/horizontal/list.txt", "horizontal/");
                        test_diagrams("./test/nested/list.txt", "nested/");
                        break;
                  case '?':
                        cerr << "Argument Error. Valid arguments:\n";
                        cerr << "   -b (basic)\n   -p (parens)\n   -h (horizontal)\n";
                        cerr << "   -n (nested)\n   -a (all)" << endl;
                  default:
                        return 1;
            }
      }
      //if no flags -- run basic test
      if (argc == 1)
            test_diagrams("./test/basic/list.txt", "basic/");
}     

void test_diagrams (string textFile, string folder){
      string command = "./src/osra -f can ./test/";
      string test;
      FILE *pipe;
      string fileName;
      string smiles;

      ifstream list (textFile.c_str());
      if (list.is_open()){
            while (true) {
                  if (list.eof())
                        break;
                  //get name of the file to test from list.txt
                  getline(list, fileName);
                  if (list.eof())
                        break;
                  test = command + folder + fileName;
                  //get 'correct' smiles string to test against osra's output from list.txt
                  getline(list, smiles);
                  pipe = popen (test.c_str(), "r");
                  char buff [ 64 ];
                  fgets (buff, sizeof (buff), pipe );
                  string s (buff);
                  s = s.erase (s.size()-1);

                  if (s == smiles){
                        //if osra's output == 'correct' smiles string
                        printf("[PASS]\n\t%s\n", s.c_str());
                  } else {
                        //output != smiles string
                        printf("[FAIL]\n\tExpected Output: %s\n\tActual output: %s\n", smiles.c_str(), s.c_str());
                  }
            }
      } else {
            cout << "Unable to open file";
      }


}
