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

class cursor {
      public:
            cursor(const Image *img, int x, int y, int components, double threshold):
                  bm(0L),c(components), t(threshold), e(true), m((1L<<(components*w))-1L), w(2L)
            {
                  ColorGray cg = img->pixelColor(x,y);
                  if(cg.shade() < t && cg.alpha() == 0) e = false;
                  _f(img, x    , y - c);
                  _f(img, x + 2, y);
                  _f(img, x    , y + 2);
                  _f(img, x - c, y + 2);
            };
            bool is_endpoint() { if(e) return false; int r=0; for(int i=0;i<c;++i,r+=_r(bm&(m<<(i*2*c)))); return r==1; };
      private:
            unsigned long c, bm, m, w;
            double t;
            bool e;
            void set_point(double s) { bm<<=1; bm+=(s<t)?1:0; };
            int _r(int d) { return d>0; };
            void _f(const Image *img, int x, int y) { for(int i=0;i<w;++i) for(int j=0;j<c;++j,set_point(ColorGray(img->pixelColor(x+i, y+j)).shade())); };
};

class bracketbox {
      public:
            /* -- bracketbox --
             * CURRENTLY ONLY WORKS ON VERTICALLY ORIENTED BRACKETS 
             * Bounding box around brackets (parenthesis) that will enclose POTRACE points to throw away.
             * p1, p2 initialize extrema of brackets
             * width - width of the bounding box (Maybe function bond distance or maybe another way to calclulate more precisely)
             * type - Either 'l'/'L' or 'r'/'R' or 'u'/'U' for respectively Left or Right or Unknown bracket orientation
             * cx, and cy denote the upper left hand corner of the box
            */
            bracketbox(const pair<int, int> p1, const pair<int, int> p2, const int width, const char type): 
                  x1(p1.first), y1(p1.second), x2(p2.first), y2(p2.second), width(width), type(type),
                  cx(abs(p1.first-width)), cy((p1.second < p2.second) ? p1.second : p2.second), height(abs(p1.second - p2.second)) {};

            bool is_inside(const int x, const int y) { 
                  bool res = false;
                  bool unknown = false;
                  switch (tolower(type)){
                        case 'l' : break;
                        case 'r' : cx+=width; break;
                        default  : type = 'l'; width*=2; break;
                  }
                  const int left = cx;
                  const int right = cx + width;
                  const int top = cy;
                  const int bottom = cy + height;
                  if(x >= left && x <= right &&
                     y >= top  && y <= bottom) 
                        res = true;
                  return res;
            };

            void plot(Image img){
                  img.pixelColor(cx, cy, "green");
                  img.pixelColor(cx+width, cy, "green");
                  img.pixelColor(cx, cy+height, "green");
                  img.pixelColor(cx+width, cy+height, "green");
                  img.write("paren_box.gif");
            };

            bool intersects(const bond_t &bond, const vector<atom_t> &atoms){
                  double atom1_x = atoms[bond.a].x;
                  double atom1_y = atoms[bond.a].y;
                  double atom2_x = atoms[bond.b].x;
                  double atom2_y = atoms[bond.b].y;
                  double midx = (atom1_x + atom2_x) / 2.0;
                  double midy = (atom1_y + atom2_y) / 2.0;
                  return is_inside((int)midx, (int)midy);
            };

      private:
            int x1, x2, y1, y2, width, height, cx, cy;
            char type;
};

void  find_intersection(vector<bond_t> &bonds, const vector<atom_t> &atoms, vector<bracketbox> &bracketboxes);
void  remove_brackets(Image img, potrace_path_t *p, vector<bracketbox> &bracketboxes);
void  david_find_endpoints(Image detect, vector<pair<int, int> > &endpoints, int width, int height, vector<pair<pair<int, int>, pair<int, int> > > &bracketpoints);
float calc_mean(const potrace_path_t *p, vector<point> &points, const pair<int, int> size, int &total, const map<const potrace_path_t *,bool> &atom_map);
float calc_stddev(const potrace_path_t *p, const vector<point> &points);
void  plot_points(Image &img, const vector<point> &points, const char **colors);
void  lt_one_stddev(vector<point> &points, const unsigned int threshold);
void  find_paren(Image img, const potrace_path_t *p, vector<atom_t> &atoms, vector<bracketbox> &bracketboxes);
void  plot_atoms(Image &img, const vector<atom_t> &atoms, const std::string color);
void  plot_bonds(Image &img, const vector<bond_t> &bonds, const vector<atom_t> atoms, const std::string color);
void  plot_atoms(Image &img, const vector<atom_t> &atoms, const std::string color);
void  plot_all(Image img, const int boxn, const string id, const vector<atom_t> atoms, const vector<bond_t> bonds, const vector<letters_t> letters, const vector<label_t> labels);
void  print_images(const potrace_path_t *p, int width, int height, const Image &box);

void find_intersection(vector<bond_t> &bonds, const vector<atom_t> &atoms, vector<bracketbox> &bracketboxes){
      for(vector<bond_t>::iterator bond = bonds.begin(); bond != bonds.end(); ++bond)
            for(vector<bracketbox>::iterator bracketbox = bracketboxes.begin(); bracketbox != bracketboxes.end(); ++bracketbox)
                  if(bond->exists) bond->split = bracketbox->intersects(*bond, atoms);
}

void remove_brackets(Image img, potrace_path_t *p, vector<bracketbox> &bracketboxes){
      vector<pair<int, int> > endpoints;
      vector<pair<pair<int, int>,pair<int, int> > > bracketpoints;
      david_find_endpoints(img, endpoints, img.columns(), img.rows(), bracketpoints);
      for(vector<pair<pair<int, int>, pair<int, int> > >::iterator itor = bracketpoints.begin(); itor != bracketpoints.end(); ++itor){
            bracketboxes.push_back(bracketbox(itor->first, itor->second, 4, 'u')); 
            for(potrace_path_t *curr = p;curr != NULL; curr = curr->next){
                  potrace_dpoint_t (*c)[3] = curr->curve.c;
                  potrace_dpoint_t (*keep_curves)[3] = (potrace_dpoint_t(*)[3])malloc(sizeof(potrace_dpoint_t[3]) * curr->curve.n);
                  int *keep_tags = (int*)malloc(sizeof(int) * curr->curve.n);
                  int ki = 0;
                  for(int i=0;i<curr->curve.n;++i){
                        if(!bracketboxes.back().is_inside(c[i][2].x, c[i][2].y)){
                              memcpy(keep_curves[ki], c[i], sizeof(potrace_dpoint_t[3]));
                              keep_tags[ki]   = curr->curve.tag[i];
                              ++ki;
                        }
                  }
                  if(ki == curr->curve.n) {
                        //free(keep_tags);
                        //free(keep_curves);
                        continue;
                  }
                  potrace_dpoint_t (*newc)[3] = (potrace_dpoint_t(*)[3])malloc(sizeof(potrace_dpoint_t[3]) * ki);
                  int *newtag = (int*)malloc(sizeof(int) * ki);
                  memcpy(newc, keep_curves, sizeof(potrace_dpoint_t[3]) * ki);
                  memcpy(newtag, keep_tags, sizeof(int) * ki);
                  free(curr->curve.c);
                  free(curr->curve.tag);
                  curr->curve.n = ki;
                  curr->curve.c = newc;
                  curr->curve.tag = newtag;
            }
      }
      for(potrace_path_t *curr = p;curr != NULL; curr = curr->next){
            potrace_dpoint_t (*c)[3] = curr->curve.c;
            for(int i=0;i<curr->curve.n;++i){
                  img.pixelColor(c[i][2].x, c[i][2].y, "green");

            }
      }
      img.write("new_potrace.gif");
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
                              cout << endpoints.at(i).first << ", " << endpoints.at(i).second << endl;
                              cout << endpoints.at(j).first << ", " << endpoints.at(j).second << endl;
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

float calc_mean(const potrace_path_t *p, vector<point> &points, const pair<int, int> size, int &total, const map<const potrace_path_t *, bool> &atom_map){
      float mean = 0.0;
      unsigned int k = 0;
      for(const potrace_path_t *curr = p;curr != NULL; curr = curr->next, ++k){
            if(atom_map.find(curr) != atom_map.end()) { if(!atom_map.find(curr)->second) continue; }
            potrace_dpoint_t (*c)[3] = curr->curve.c;
            for(int i=0;i<curr->curve.n;++i, ++total){
                  if(i != 0){
                        int x0 = c[i-1][2].x, y0 = c[i-1][0].y;  // x, y of previous point / First point
                        int x1 = c[ i ][0].x, y1 = c[ i ][0].y;  // x, y of first handle
                        int x2 = c[ i ][1].x, y2 = c[ i ][1].y;  // x, y of second handle
                        int x3 = c[ i ][2].x, y3 = c[ i ][2].y;  // x, y of point
                        if(x0 < 0 || x0 > size.first ) continue; // if bad x, y neglect point
                        if(x1 < 0 || x1 > size.first ) continue;
                        if(x2 < 0 || x2 > size.first ) continue;
                        if(x3 < 0 || x3 > size.first ) continue;
                        if(y0 < 0 || y0 > size.second) continue;
                        if(y1 < 0 || y1 > size.second) continue;
                        if(y2 < 0 || y2 > size.second) continue;
                        if(y3 < 0 || y3 > size.second) continue;
                        float dis1 = 0.0;
                        float dis2 = 0.0;
                        float dis3 = 0.0;
                        if(curr->curve.tag[i] == POTRACE_CURVETO) { 
                              dis1 = sqrt(pow((x1 - x0), 2.0) + pow((y1 - y0), 2.0)); 
                              dis2 = sqrt(pow((x1 - x2), 2.0) + pow((y1 - y2), 2.0)); 
                        } else {
                              dis1 = sqrt(pow((x2 - x0), 2.0) + pow((y2 - y0), 2.0)); 
                        }
                        dis3 = sqrt(pow((x2 - x3), 2.0) + pow((y2 - y3), 2.0));
                        if(dis1==dis1) { mean += dis1; points.push_back(point(x1, y1, dis1, CLEAR)); ++total; } else continue;
                        if(dis2==dis2) { mean += dis2; points.push_back(point(x2, y2, dis2, CLEAR)); ++total; } else continue;
                        if(dis3==dis3) { mean += dis3; points.push_back(point(x3, y3, dis3, CLEAR)); ++total; } else continue;
                  }
            }
      }
      return (mean / total);
}

float calc_stddev(const float mean, const vector<point> &points, const int total){
      float variance = 0.0;
      for(vector<point>::const_iterator itor = points.begin(); itor != points.end(); ++itor)
            variance += pow(itor->d - mean, 2.0);
      return sqrt(variance / total);
}

void lt_one_stddev(vector<point> &points, const unsigned int threshold, char color){
      for(vector<point>::iterator itor = points.begin(); itor != points.end(); ++itor)
            if((int)itor->d < threshold) itor->color = color; 
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

void find_paren(Image img, const potrace_path_t *p, vector<atom_t> &atoms, vector<bracketbox> &bracketboxes){ 
      /*
      std::cout << atoms.size() << endl;
      map<const potrace_path_t *, bool> atom_map;
      for(vector<atom_t>::const_iterator itor = atoms.begin(); itor != atoms.end(); ++itor)
            atom_map[itor->curve] = itor->exists;
      vector<point> points;
      const int num_stddevs = 1;
      int total;
      const float mean = calc_mean(p, points, pair<int, int>(img.columns(), img.rows()), total, atom_map);
      const float stddev = calc_stddev(mean, points, total);
      const unsigned int threshold = abs((int)(mean - stddev*num_stddevs));
      std::cout << mean << " " << stddev << " " << threshold << " " << total << std::endl;
      lt_one_stddev(points, 12, BLUE);
      plot_points(img, points);
      */
      vector<pair<int, int> > endpoints;
      vector<pair<pair<int, int>,pair<int, int> > > bracketpoints;
      david_find_endpoints(img, endpoints, img.columns(), img.rows(), bracketpoints);
      for(vector<pair<pair<int, int>, pair<int, int> > >::iterator itor = bracketpoints.begin(); itor != bracketpoints.end(); ++itor){
            bracketboxes.push_back(bracketbox(itor->first, itor->second, 4, 'u')); 
            for(vector<atom_t>::iterator atom = atoms.begin(); atom != atoms.end(); ++atom){
                  if(bracketboxes.back().is_inside(atom->x, atom->y)) {
                        atom->exists = false;
                        atom->ignore = true;
                        img.pixelColor(atom->x, atom->y, "blue");
                  }
            }
      }
      bracketboxes[0].plot(img);
      bracketboxes[1].plot(img);
      //find_endpoints(img, endpoints, 1.0, GREEN);
      //plot_points(img, endpoints);
      img.write("paren_plot.gif");
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
                  img.pixelColor(x, y, color);
                  if(itor->split) img.pixelColor(x, y, "green");
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
