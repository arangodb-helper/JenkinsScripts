#!groovy

def CONTAINERS=[
 [ 'docker': true,  'name': 'centosix', 'packageFormat': 'RPM'],
 [ 'docker': true,  'name': 'centoseven', 'packageFormat': 'RPM'],
 [ 'docker': true,  'name': 'opensusethirteen', 'packageFormat': 'RPM'],
 [ 'docker': true,  'name': 'debianjessie', 'packageFormat': 'DEB'],
 [ 'docker': true,  'name': 'ubuntufourteenofour', 'packageFormat': 'DEB'],
 [ 'docker': true,  'name': 'ubuntusixteenofour', 'packageFormat': 'DEB'],
]
