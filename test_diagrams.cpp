//Tests multiple diagrams through OSRA 
#include <iostream>
#include <stdio.h>
#include <vector>
using namespace std;

int main(int argc, char **argv){
      string command = "./src/osra -f can ./test/chemdraw/";
      string test;
      int numFiles = 10;
      FILE *pipe;
      string diagrams[] = {"4-chlorostyrene.gif", "8-caffeine.gif", "Clorgyline.gif",
                           "diethoxymethyl_acetate.gif", "Diphenyl_N-ethanethiol_pyrrole.gif",
                           "myristyl_nicotinate.gif", "octachlorostyrene.gif", "phenyl_salicylate.gif", 
                           "Propyl_Gallate.gif", "Tris_nitromethane.gif" };
      string SMILES[] = {
                  "C=Cc1ccc(cc1)Cl",
                  "Clc1cccc(c1)/C=C/c1nc2c(n1C)c(=O)n(c(=O)n2C)C",
                  "C#CCN(CCCOc1ccc(cc1Cl)Cl)C",
                  "CCOC(OC(=O)C)OCC",
                  "SCCn1c(ccc1c1ccccc1)c1ccccc1",
                  "CCCCCCCC",
                  "ClC(=C(c1c(Cl)c(Cl)c(c(c1Cl)Cl)Cl)Cl)Cl",
                  "O=C(c1ccccc1O)Oc1ccccc1",
                  "CCCOC(=O)c1cc(O)c(c(c1)O)O",
                  "N#CCCC([N+](=O)[OH2+])(CCC(=N)C)CCC#N",
      };
      for(int i = 0; i < numFiles; ++i){
            test = command + diagrams[i];
            pipe = popen (test.c_str(), "r");
            char buff [ 64 ];
            fgets (buff, sizeof (buff), pipe );
            string s (buff);
            s = s.erase (s.size()-1);
            if(s == SMILES[i])
                  printf ("[PASS]\n\t%s\n", s.c_str());
            else
                  printf ("[FAIL]\n\tExpected Output: %s\n\tActual output: %s\n", SMILES[i].c_str(), s.c_str());
            pclose(pipe);
      }
}      
