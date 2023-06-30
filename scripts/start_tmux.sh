session=xtdb-money

tmux new-session -d -s $session

# REPL window
tmux rename-window -t 0 'repl'
tmux send-keys 'clear' C-m 'lein repl' C-m

# tmux split-window -v
# tmux send-keys 'lein figwheel' C-m

# Code window
tmux new-window -t $session:1 -n $session
tmux send-keys 'nvim' C-m
tmux split-window -h
tmux send-keys 'git status' C-m

# SQL window
tmux new-window -t $session:2 -n 'database' 'psql -d xtdb_money_development'

# Log window
tmux new-window -t $session:3 -n 'logs'
tmux send-keys 'tail -f log/development.log | grep -e ERROR -e WARN -e dbk' C-m
tmux split-window -v
tmux send-keys 'tail -f log/development.log' C-m

tmux attach -t $session:1
