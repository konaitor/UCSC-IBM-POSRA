#include <Magick++.h>
extern "C" {
#include <potracelib.h>
#include <pgm2asc.h>
}
#include <stdio.h> // fclose
#include <stdlib.h> // malloc(), free()
#include <math.h> // fabs(double)
#include <list> // sdt::list
#include <vector> // std::vector
#include <map>
#include <set>
#include <algorithm> // std::sort, std::min(double, double), std::max(double, double)
#include <iostream> // std::ostream, std::cout
#include <fstream> // std::ofstream, std::ifstream
#include <sstream>
#include <ctype.h>
#include "osra.h"
#include "osra_labels.h"

static const string colors[] = { "firebrick", "crimson", "darkred",   "brown", 
                                 "magenta",   "SkyBlue", "turquoise", "gold", 
                                 "orange",    "red",     "green",     "yellow", 
                                 "pink",      "lime",    "cyan",      "indigo", 
                                 "blue", };
enum color_index {
      FIREBRICK = 0,
      CRIMSON   = 1,
      DARKRED   = 2,
      BROWN     = 3,
      MAGENTA   = 4,
      SKYBLUE   = 5,
      TURQUOISE = 6,
      GOLD      = 7,
      ORANGE    = 8,
      RED       = 9,
      GREEN     = 10,
      YELLOW    = 11,
      PINK      = 12,
      LIME      = 13,
      CYAN      = 14,
      INDIGO    = 15, 
      BLUE      = 16, 
      CLEAR     = 255,
};

class Bracket;
class Polymer;
class point;

/* Polymer
 * General data sctructure to hold pertainent information, interfacing with the database.
*/
class Polymer {
      public:
            Polymer(){};

            void set_SMILES(string SMILES) {
                  this->SMILES = SMILES;
            };

            void set_file_name(string file_name) {
                  this->file_name = file_name;
            };

            void set_degree(const string degree) {
                  this->degree = degree;
            };

            string get_degree() {
                  return this->degree;
            }

            vector<pair<Bracket, Bracket> > brackets;

      private:
            string SMILES;
            string file_name;
            string degree;
};

class point {
      public:
            point(){};
            point(float x, float y, float d, char c):x(x), y(y), d(d), color(c){};
            float x, y, d;
            unsigned char color;
};

class Bracket {
      public:
            /* -- Bracket --
             * CURRENTLY ONLY WORKS ON VERTICALLY ORIENTED BRACKETS 
             * Bounding box around brackets (parenthesis) that will enclose POTRACE points to throw away.
             * p1, p2 initialize extrema of brackets (Endpoints)
             * orientation - Either 'l'/'L' or 'r'/'R' or 'u'/'U' for respectively Left or Right or Unknown bracket orientation
             * tlx, and tly denote the upper left hand corner of the box
             * brx, and bry denote the lower right hand corner of the box
             * cx*, cy* represent where the bond is broken by the box
            */
            Bracket(){};

            Bracket(const pair<int, int> p1, const pair<int, int> p2, Image img): 
                  x1(p1.first), y1(p1.second), x2(p2.first), y2(p2.second) 
            { 
                  height = abs(y1 - y2);
                  tly = (y1 < y2) ? y1 : y2;
                  bry = tly + height + 1;
                  int threshold = height / 2;
                  int l_confidence = 0;
                  int r_confidence = 0;
                  int l_width = 0;
                  int r_width = 0;
                  int l_cx2 = 0;
                  int l_cy2 = 0;
                  int r_cx2 = 0;
                  int r_cy2 = 0;
                  bool found = false;
                  for(int y = tly; y < bry; ++y){
                        //Record bond location
                        if(!found && abs(y-tly) > height / 4 && ColorGray(img.pixelColor(x1, y)).shade() < 1.0){
                              cx1 = x1;
                              cy1 = y;
                              found = true;
                        }
                        //Check left
                        for(int x = x1; x > (x1 - threshold); --x)
                              if(ColorGray(img.pixelColor(x, y)).shade() < 1.0){
                                    ++l_confidence;
                                    if(abs(x - x1) > l_width) {
                                          l_width = abs(x - x1);
                                          l_cx2 = x;
                                          l_cy2 = y;
                                    }
                                    break;
                              }
                        //Check right
                        for(int x = x1; x < (x1 + threshold); ++x)
                              if(ColorGray(img.pixelColor(x, y)).shade() < 1.0){
                                    ++r_confidence;
                                    if(abs(x - x1) > r_width){ 
                                          r_width = abs(x - x1);
                                          r_cx2 = x;
                                          r_cy2 = y;
                                    }
                                    break;
                              }
                  }
                  if(l_confidence > r_confidence){
                        orientation = 'l';
                        width = l_width;
                        tlx = x1 - width - 1;
                        brx = x2;
                        ++cx1;
                        cx2 = l_cx2 - 1;
                        cy2 = l_cy2;
                        for(int y = tly; y < bry; ++y)
                              if(ColorGray(img.pixelColor(tlx - 1, y)).shade() < 1.0){
                                    cx2 = tlx - 1;
                                    cy2 = y;
                                    break;
                              }
                  }else{
                        orientation = 'r';
                        width = r_width;
                        tlx = x1;
                        brx = x2 + width + 1;
                        --cx1;
                        cx2 = r_cx2 + 1;
                        cy2 = r_cy2;
                        for(int y = tly; y < bry; ++y)
                              if(ColorGray(img.pixelColor(brx + 1, y)).shade() < 1.0){
                                    cx2 = brx + 1;
                                    cy2 = y;
                                    break;
                              }
                  }
            };

            void remove_brackets(Image &img){
                  img.fillColor("white");
                  img.strokeColor("white");
                  img.strokeWidth(0.0);
                  img.draw(DrawableRectangle(tlx, tly, brx, bry));
                  img.strokeColor("black");
                  img.strokeWidth(1.0);
                  img.draw(DrawableLine((double)cx1, (double)cy1, (double)cx2, (double)cy2));
            };

            bool intersects(const bond_t &bond, const vector<atom_t> &atoms){
                  double ax1 = atoms[bond.a].x;
                  double ay1 = atoms[bond.a].y;
                  double ax2 = atoms[bond.b].x;
                  double ay2 = atoms[bond.b].y;
                  double right = (ax1 > ax2) ? ax1 : ax2;
                  double left  = (ax1 < ax2) ? ax1 : ax2;
                  double midy  = (ay1 + ay2) / 2.0;
                  return (x1 < right && x1 > left && midy > tly && midy < bry);
            };

            char get_orientation() const {
                  return orientation;
            }

      private:
            int x1, x2, y1, y2, width, height, tlx, tly, brx, bry, cx1, cy1, cx2, cy2;
            char orientation;
};

/** Edit Smiles
  *  Take the resulting smiles string from OSRA and splice and format the string
  *  by removing pseudo poloniums and replacing them with respective end group
  *  or repeat unit identifiers.
*/
void  edit_smiles(string &s);

/** Find Degree
  *  Search through characters and strings that OSRA could not associate, and 
  *  try and associate those to a bracket.  Looks specifically for integers
  *  or typical degree characters, i.e. 'n', 'x', 'y', etc.
*/
void  find_degree(Polymer &, const vector<letters_t>, const vector<label_t>);

/** Find Intersection
  *  Iterate through all of the bonds in the structure and determine those of
  *  which that intersect a bracket and will therefore be split.  A bond that
  *  is marked to be split is a boundary between a repeat unit and an end group
  *  and these need to be distinguished.
*/
void  find_intersection(vector<bond_t> &bonds, const vector<atom_t> &atoms, vector<Bracket> &bracketboxes);

/** Get Potrace Points
  *  Converts potrace control points into a vector of pairs where each pair is
  *  a control point.  This makes it easier to access later during the find
  *  endpoints processing.  Since potrace has done a lot of the work for us,
  *  this should speed up and reduce errors during endpoint detection.
*/
vector<pair<int, int> > get_potrace_points(const potrace_path_t *p);

/** Pair Brackets
  *  After all the brackets have been found, and after OSRA has done most of the
  *  image processing for that matter, we want to associate pairs of brackets
  *  so they can be properly matched with their associative repeat / end groups.
*/
void  pair_brackets(Polymer &, const vector<Bracket> &);

/** Split Atom
  *  This function takes the structure that was just compiled in OSRA and splits
  *  the structure at appropriate boundaries, that is between repeat units and
  *  end groups.  At these boundaries a bivalent atom is substituted so that
  *  OpenBabel can still parse the structure as a contiguous *monomer*, in this
  *  case the bivalent atoms are Polonium (Po) for left oriented brackets and
  *  Livermorium (Lv) for right oriented brackets.  This is important, because
  *  later we will split the resulting SMILES string appropriately to accurately
  *  reflect the inputted Polymer.
*/
void  split_atom(vector<bond_t> &bonds, vector<atom_t> &atoms, int &n_atom, int &n_bond);

/** Find Endpoints
  *  Scan through the entire image on a pixel level and detect endpoints.  Endpoints
  *  are good indicators as to where a bracket exists.  After all endpoints are found
  *  we can look for symmetries in the diagram as bracket pairs naturally have both
  *  a vertical and horizontal symmetry which is rare on most chemical diagrams.
*/
void find_endpoints(Image detect, vector<pair<int, int> > &potrace_points, vector<pair<int, int> > &endpoints, int width, int height, vector<pair<pair<int, int>, pair<int, int> > > &bracketpoints);

/** Find Brackets
  *  The main entry point from OSRA to POSRA.  Encapsulates many of the functions
  *  above.
*/
void  find_brackets(Image &img, const potrace_path_t *p, vector<Bracket> &bracketboxes);

/** The following functions are utility functions for writing images with useful
  * information for debugging.
*/
void  plot_points(Image &img, const vector<point> &points, const char **colors);

void  plot_atoms(Image &img, const vector<atom_t> &atoms, const std::string color);

void  plot_bonds(Image &img, const vector<bond_t> &bonds, const vector<atom_t> atoms, const std::string color);

void  plot_atoms(Image &img, const vector<atom_t> &atoms, const std::string color);

void  plot_all(Image img, const int boxn, const string id, const vector<atom_t> atoms, const vector<bond_t> bonds, const vector<letters_t> letters, const vector<label_t> labels);

void  print_images(const potrace_path_t *p, int width, int height, const Image &box);
