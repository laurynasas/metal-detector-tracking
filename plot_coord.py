import matplotlib as mpl
from mpl_toolkits.mplot3d import Axes3D
import numpy as np
import matplotlib.pyplot as plt

mpl.rcParams['legend.fontsize'] = 10

fig = plt.figure()
ax = fig.gca(projection='3d')
# theta = np.linspace(-4 * np.pi, 4 * np.pi, 100)
# z = np.linspace(-2, 2, 100)
# r = z**2 + 1
# x = r * np.sin(theta)
# y = r * np.cos(theta)

f = open("./coordinates.txt")

data = f.readlines()

all_x = []
all_y = []
all_z = []

for line in data:
	(x,y,z,time) = line.split("; ")
	all_x.append(float(x))
	all_y.append(float(1))
	all_z.append(float(z))

ax.plot(all_x, all_y, all_z, label='Coil motion')
ax.legend()

plt.show()
