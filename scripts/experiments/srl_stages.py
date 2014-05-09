'''
Defines the SRL stages for an experiment pipeline.
@author: mgormley
'''
import sys
import os
import getopt
import math
import tempfile
import stat
import shlex
import subprocess
from subprocess import Popen
from optparse import OptionParser
from experiments.core.util import get_new_file, sweep_mult, fancify_cmd, frange
from experiments.core.util import head_sentences
import platform
from glob import glob
from experiments.core.experiment_runner import ExpParamsRunner, get_subset
from experiments.core import experiment_runner
from experiments.core import pipeline
import re
import random
from experiments.core.pipeline import write_script, RootStage, Stage
import multiprocessing
from experiments.exp_util import *
from experiments.path_defs import *

# ---------------------------- Experiment/Stage Classes ----------------------------------

class SrlExpParams(experiment_runner.JavaExpParams):
    
    def __init__(self, **keywords):
        experiment_runner.JavaExpParams.__init__(self,keywords)
            
    def get_initial_keys(self):
        return "tagger_parser".split()
    
    def get_instance(self):
        return SrlExpParams()
    
    def create_experiment_script(self, exp_dir):
        script = "\n"
        #script += 'echo "CLASSPATH=$CLASSPATH"\n'
        if self.get("oldJar"): # TODO: remove this after backwards compat not needed.
            script += 'CLASSPATH=%s:$CLASSPATH\n' % (self.get("oldJar"))
            script += 'echo "CLASSPATH=$CLASSPATH"\n'
        cmd = "java " + self.get_java_args() + " edu.jhu.srl.SrlRunner  %s \n" % (self.get_args())
        script += fancify_cmd(cmd)
        
        if self.get("train") and self.get("trainType") == "CONLL_2009":
            script += self.get_eval09_script("train", True)
            script += self.get_eval09_script("train", False)
        if (self.get("dev") or self.get("propTrainAsDev") > 0) and self.get("devType") == "CONLL_2009":
            script += self.get_eval09_script("dev", True)
            script += self.get_eval09_script("dev", False)
        if self.get("test") and self.get("testType") == "CONLL_2009":
            script += self.get_eval09_script("test", True)
            script += self.get_eval09_script("test", False)
                        
        if self.get("train") and self.get("trainType") == "CONLL_X":
            script += self.get_eval07_script("train")
        if (self.get("dev") or self.get("propTrainAsDev") > 0) and self.get("devType") == "CONLL_X":
            script += self.get_eval07_script("dev")
        if self.get("test") and self.get("testType") == "CONLL_X":
            script += self.get_eval07_script("test")
        
        return script
    
    def get_eval09_script(self, data_name, predict_sense):    
        script = "\n"
        script += 'echo "Evaluating %s with predict_sense=%r"\n' % (data_name, predict_sense)
        eval_args = "" 
        eval_args += " -g " + self.get(data_name + "GoldOut") + " -s " + self.get(data_name + "PredOut")
        if predict_sense:
            eval_out = data_name + "-eval.out"
            script += "perl %s/scripts/eval/eval09.pl %s &> %s\n" % (self.root_dir, eval_args, eval_out)
        else:
            eval_out = data_name + "-no-sense" + "-eval.out"
            script += "perl %s/scripts/eval/eval09-no_sense.pl %s &> %s\n" % (self.root_dir, eval_args, eval_out)
        script += 'grep --after-context 5 "SYNTACTIC SCORES:" %s\n' % (eval_out)
        script += 'grep --after-context 11 "SEMANTIC SCORES:" %s\n' % (eval_out)        
        return script
    
    def get_eval07_script(self, data_name):    
        script = "\n"
        script += 'echo "Evaluating %s"\n' % (data_name)
        eval_args = "" 
        eval_args += " -g " + self.get(data_name + "GoldOut") + " -s " + self.get(data_name + "PredOut")
        eval_out = data_name + "-eval.out"
        script += "perl %s/scripts/eval/eval07.pl %s &> %s\n" % (self.root_dir, eval_args, eval_out)
        script += 'grep --after-context 3 "Labeled   attachment score:" %s\n' % (eval_out)        
        return script
    
    def get_java_args(self):
        return self._get_java_args(self.work_mem_megs)


class ScrapeSrl(experiment_runner.PythonExpParams):
    
    def __init__(self, **keywords):
        experiment_runner.PythonExpParams.__init__(self,keywords)
        self.always_relaunch()

    def get_initial_keys(self):
        return "tagger_parser model k s".split()
    
    def get_instance(self):
        return ScrapeSrl()
    
    def get_name(self):
        return "scrape_srl"
    
    def create_experiment_script(self, exp_dir):
        self.add_arg(os.path.dirname(exp_dir))
        script = ""
        cmd = "python %s/scripts/experiments/scrape_srl.py %s\n" % (self.root_dir, self.get_args())
        script += fancify_cmd(cmd)
        return script
