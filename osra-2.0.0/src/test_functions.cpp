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

using namespace std;
using namespace Magick;


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

class point {
      public:
            point(){};
            point(float x, float y, float d, char c):x(x), y(y), d(d), color(c){};
            float x, y, d;
            unsigned char color;
};

class bracketbox {
      public:
            /* -- bracketbox --
             * CURRENTLY ONLY WORKS ON VERTICALLY ORIENTED BRACKETS 
             * Bounding box around brackets (parenthesis) that will enclose POTRACE points to throw away.
             * p1, p2 initialize extrema of brackets
             * width - width of the bounding box (Maybe function bond distance or maybe another way to calclulate more precisely)
             * type - Either 'l'/'L' or 'r'/'R' or 'u'/'U' for respectively Left or Right or Unknown bracket orientation
             * tlx, and tly denote the upper left hand corner of the box
             * brx, and bry denote the lower right hand corner of the box
             * cx*, cy* represent where the bond is broken by the box
            */
            bracketbox(const pair<int, int> p1, const pair<int, int> p2, Image img): 
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
                        type = 'l';
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
                        type = 'r';
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

      private:
            int x1, x2, y1, y2, width, height, tlx, tly, brx, bry, cx1, cy1, cx2, cy2;
            char type;
};

void  find_intersection(vector<bond_t> &bonds, const vector<atom_t> &atoms, vector<bracketbox> &bracketboxes);
void  split_atom(vector<bond_t> &bonds, vector<atom_t> &atoms, int &n_atom, int &n_bond);
void  david_find_endpoints(Image detect, vector<pair<int, int> > &endpoints, int width, int height, vector<pair<pair<int, int>, pair<int, int> > > &bracketpoints);
void  plot_points(Image &img, const vector<point> &points, const char **colors);
void  find_paren(Image &img, const potrace_path_t *p, vector<atom_t> &atoms, vector<bracketbox> &bracketboxes);
void  find_brackets(Image &img, vector<bracketbox> &bracketboxes);
void  plot_atoms(Image &img, const vector<atom_t> &atoms, const std::string color);
void  plot_bonds(Image &img, const vector<bond_t> &bonds, const vector<atom_t> atoms, const std::string color);
void  plot_atoms(Image &img, const vector<atom_t> &atoms, const std::string color);
void  plot_all(Image img, const int boxn, const string id, const vector<atom_t> atoms, const vector<bond_t> bonds, const vector<letters_t> letters, const vector<label_t> labels);
void  print_images(const potrace_path_t *p, int width, int height, const Image &box);

void find_intersection(vector<bond_t> &bonds, const vector<atom_t> &atoms, vector<bracketbox> &bracketboxes){
      if(bracketboxes.size() != 2) return;
      for(vector<bond_t>::iterator bond = bonds.begin(); bond != bonds.end(); ++bond)
            if(bond->exists) bond->split = (bracketboxes[0].intersects(*bond, atoms) || bracketboxes[1].intersects(*bond, atoms));
}

void  split_atom(vector<bond_t> &bonds, vector<atom_t> &atoms, int &n_atom, int &n_bond){
      for(vector<bond_t>::iterator bond = bonds.begin(); bond != bonds.end(); ++bond){
            if(bond->split){
                  ++n_atom;
                  ++n_bond;
                  double x = (atoms[bond->a].x + atoms[bond->b].x) / 2;  
                  double y = (atoms[bond->a].y + atoms[bond->b].y) / 2;  
                  atom_t *POLONIUM = new atom_t(x, y, bond->curve);
                  POLONIUM->exists = true;
                  POLONIUM->label = "Po";
                  atoms.push_back(*POLONIUM);
                  bond_t *newbond = new bond_t(atoms.size()-1, bond->b, bond->curve);
                  bonds.push_back(*newbond);
                  bond->b = atoms.size() - 1;
            }
      }
}

void david_find_endpoints(Image detect, vector<pair<int, int> > &endpoints, int width, int height, vector<pair<pair<int, int>, pair<int, int> > > &bracketpoints){
      const unsigned int SIDE_GROUP_SIZE = 2;
      const unsigned int BRACKET_MIN_SIZE = 5;
      for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                  int adj_groups = 0; // The number of side groups that contain at least 1 non-white pixel
                  vector<ColorGray> north, south, east, west;
                  vector<vector<ColorGray > > sides;

                  // Populate the side-groups vectors with the correct Color objects
                  for (int n = 1; n <= SIDE_GROUP_SIZE; n++ ) {
                        north.push_back (detect.pixelColor(i,j-n));
                        north.push_back (detect.pixelColor(i+1,j-n));
                        west.push_back (detect.pixelColor(i-n,j));
                        west.push_back (detect.pixelColor(i-n,j+1));
                        south.push_back (detect.pixelColor(i,j+(n+1)));
                        south.push_back (detect.pixelColor(i+1,j+(n+1)));
                        east.push_back (detect.pixelColor(i+(n+1),j));
                        east.push_back (detect.pixelColor(i+(n+1),j+1));
                  }
                  // Add each side-group vector the the sides vector
                  sides.push_back(north); sides.push_back(south); sides.push_back(east); sides.push_back(west);

                  // Check if each side group contains at least 1 non-white pixel
                  for (int c = 0; c < sides.size(); c++) {
                        for (int d = 0; d < sides.at(c).size(); d++) {
                              if (sides.at(c).at(d).shade() < 1) {
                                    adj_groups++;
                                    break;
                              }
                        }
                  }

                  // If the current pixel, or the pixel to its immediate right are non-white,
                  // and 3 of the adjacent groups are completely white, then add the current
                  // pixel (or immediate right) to list of endpoints.
                  ColorGray current_pixel = detect.pixelColor(i,j);
                  ColorGray current_right = detect.pixelColor(i+1,j);
                  if (adj_groups == 1 && (current_pixel.shade() < 1 || current_right.shade() < 1)) {
                        if (current_pixel.shade() < 1 && current_right.shade() == 1)
                              endpoints.push_back(make_pair(i,j));
                        else if (current_pixel.shade() == 1 && current_right.shade() < 1)
                              endpoints.push_back(make_pair(i+1,j));
                  }
            }
      }

      // For each endpoint, loop through all other endpoints
      for (int i = 0; i < endpoints.size(); i++) {
            // Uncomment next line to show all endpoints:
            //detect.pixelColor (endpoints.at(i).first, endpoints.at(i).second, "red");
            for (int j = 0; j < endpoints.size(); j++) {
                  // If x values are equivalent (+/- 1) and the endpoints are reasonable distance apart
                  // then procede to next test
                  if ((endpoints.at(i).first == endpoints.at(j).first ||
                                    endpoints.at(i).first == endpoints.at(j).first + 1 ||
                                    endpoints.at(i).first == endpoints.at(j).first - 1) && i != j &&
                              abs (endpoints.at(i).second - endpoints.at(j).second) > BRACKET_MIN_SIZE) {

                        // Get color information of middle pixel (+/- 1) between the 2 endpoints
                        // and quarter point pixels
                        ColorGray mid0, mid1, mid2, qtr0, qtr1;
                        int mid_y = (endpoints.at(i).second + endpoints.at(j).second) / 2;
                        mid0 = detect.pixelColor(endpoints.at(i).first, mid_y);
                        mid1 = detect.pixelColor(endpoints.at(i).first, mid_y+1);
                        mid2 = detect.pixelColor(endpoints.at(i).first, mid_y-1);
                        qtr0 = detect.pixelColor(endpoints.at(i).first, (mid_y + endpoints.at(i).second)/2);
                        qtr1 = detect.pixelColor(endpoints.at(j).first, (mid_y + endpoints.at(j).second)/2);

                        // If at least one of the middle pixels are non-white and both of the quarter
                        // pixels are white, then add pair as endpoint
                        if ((mid0.shade() < 1 || mid1.shade() < 1 || mid2.shade() < 1) && qtr0.shade() == 1 && qtr1.shade() == 1) {

                              // Drawing the green line between endpoints
                              if (endpoints.at(j).second > endpoints.at(i).second) {
                                    for (int b = endpoints.at(i).second; b < endpoints.at(j).second; b++)
                                          detect.pixelColor (endpoints.at(j).first, b, "green");
                              } else {
                                    for (int b = endpoints.at(j).second; b < endpoints.at(i).second; b++)
                                          detect.pixelColor (endpoints.at(j).first, b, "green");
                              }
                              detect.pixelColor (endpoints.at(i).first, endpoints.at(i).second, "blue");
                              detect.pixelColor (endpoints.at(j).first, endpoints.at(j).second, "blue");

                              // Uncomment the next 2 lines to print the coords of the endpoints that have been
                              // determined to represent a bracket:
                              //cout << endpoints.at(i).first << ", " << endpoints.at(i).second << endl;
                              //cout << endpoints.at(j).first << ", " << endpoints.at(j).second << endl;
                              bracketpoints.push_back(make_pair(endpoints.at(i), endpoints.at(j)));

                              // Remove the detected endpoints from vector
                              if (i > j) {
                                    endpoints.erase(endpoints.begin() + i);
                                    endpoints.erase(endpoints.begin() + j);
                              }
                              else {
                                    endpoints.erase(endpoints.begin() + j);
                                    endpoints.erase(endpoints.begin() + i);
                              }
                        }                                   
                  }
            }
      }
      detect.write("out.png");
}

void find_brackets(Image &img, vector<bracketbox> &bracketboxes){ 
      vector<pair<int, int> > endpoints;
      vector<pair<pair<int, int>,pair<int, int> > > bracketpoints;
      david_find_endpoints(img, endpoints, img.columns(), img.rows(), bracketpoints);
      if(bracketpoints.size() != 2) return;
      for(vector<pair<pair<int, int>, pair<int, int> > >::iterator itor = bracketpoints.begin(); itor != bracketpoints.end(); ++itor)
            bracketboxes.push_back(bracketbox(itor->first, itor->second, img)); 
      //bracketboxes[bracketboxes.size() - 2].remove_brackets(img);
      //bracketboxes[bracketboxes.size() - 1].remove_brackets(img);
      bracketboxes[0].remove_brackets(img);
      bracketboxes[1].remove_brackets(img);
}

void plot_points(Image &img, const vector<point> &points){
      for(vector<point>::const_iterator itor = points.begin(); itor != points.end(); ++itor)
            if(itor->color != CLEAR){
                  int x = itor->x, y = itor->y;
                  if(x < 0)             x = 0;
                  if(x > img.columns()) x = img.columns() - 1;
                  if(y < 0)             y = 0;
                  if(y > img.rows())    y = img.rows() - 1;
                  img.pixelColor(x, y, colors[itor->color]);
            }
}

void plot_atoms(Image &img, const vector<atom_t> &atoms, const std::string color){
      for(vector<atom_t>::const_iterator itor = atoms.begin(); itor != atoms.end(); ++itor)
            if(itor->exists) img.pixelColor(itor->x, itor->y, color);
}

void plot_bonds(Image &img, const vector<bond_t> &bonds, const vector<atom_t> atoms, const std::string color){
      for(vector<bond_t>::const_iterator itor = bonds.begin(); itor != bonds.end(); ++itor)
            if(itor->exists){
                  int x = (atoms[itor->a].x + atoms[itor->b].x) / 2;
                  int y = (atoms[itor->a].y + atoms[itor->b].y) / 2;
                  if(itor->split) img.pixelColor(x, y, "green");
                  else img.pixelColor(x, y, color);
            }
}

void plot_letters(Image &img, const vector<letters_t> &letters, const std::string color){
      for(vector<letters_t>::const_iterator itor = letters.begin(); itor != letters.end(); ++itor)
            img.pixelColor(itor->x, itor->y, color);
}

void plot_labels(Image &img, const vector<label_t> &labels, const std::string color){
      for(vector<label_t>::const_iterator itor = labels.begin(); itor != labels.end(); ++itor){
            img.pixelColor(itor->x1, itor->y1, color);
            img.pixelColor(itor->x2, itor->y2, color);
      }
}

void plot_all(Image img, const int boxn, const string id, const vector<atom_t> atoms, const vector<bond_t> bonds, const vector<letters_t> letters, const vector<label_t> labels){
      plot_atoms(img, atoms, "blue");
      plot_bonds(img, bonds, atoms, "red");
      plot_letters(img, letters, "orange");
      plot_labels(img, labels, "pink");
      ostringstream ss;
      ss << boxn;
      img.write("plot_all_box_" + ss.str() + "_" + id + ".png");
}

