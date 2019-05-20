#! /usr/bin/env python3

from flask import Flask, request
import subprocess

app = Flask(__name__)


@app.route('/solve', methods=['GET'])
def solve():
    result = subprocess.run(['./FF/ff', '-o',  'operator', '-f', 'facts'],
                            stdout=subprocess.PIPE)

    return result.stdout


@app.route('/domain', methods=['POST'])
def upload_of_operator():
    return upload_file('operator')


@app.route('/problem', methods=['POST'])
def upload_of_facts():
    return upload_file('facts')


def upload_file(save_as):
    text = request.get_data(as_text=True)

    fh = open(save_as, 'w')
    fh.write(text)
    fh.close()

    return 'Upload Successful'


def main():
    app.run()


if __name__ == '__main__':
    main()
